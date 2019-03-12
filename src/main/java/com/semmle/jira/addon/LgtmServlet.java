package com.semmle.jira.addon;

import com.atlassian.jira.bc.ServiceResult;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.jira.workflow.TransitionOptions.Builder;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.config.InvalidConfigurationException;
import com.semmle.jira.addon.config.ProcessedConfig;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.JiraUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;

public class LgtmServlet extends HttpServlet {
  /** */
  private static final long serialVersionUID = -1528900525848515993L;

  @ComponentImport private final PluginSettingsFactory pluginSettingsFactory;
  @ComponentImport private final TransactionTemplate transactionTemplate;

  @Inject
  LgtmServlet(
      PluginSettingsFactory pluginSettingsFactory, TransactionTemplate transactionTemplate) {
    this.pluginSettingsFactory = pluginSettingsFactory;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json");

    String pathInfo = req.getPathInfo();
    String[] pathParts = pathInfo.split("/");
    String configKey = pathParts[1];

    byte[] bytes = IOUtils.toByteArray(req.getInputStream());

    ProcessedConfig config =
        validateRequest(req.getHeader("x-lgtm-signature"), bytes, resp, configKey);
    if (config == null) {
      return;
    }

    Request request;
    try {
      request = Util.JSON.readValue(bytes, Request.class);
    } catch (IOException e) {
      String message = e.getCause() != null ? " - " + e.getCause().getMessage() : "";
      sendError(
          resp, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request body: " + message);
      return;
    }

    if (!request.isValid()) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request.");
      return;
    }

    ApplicationUser user = config.getUser();
    ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(user);

    MutableIssue issue =
        ComponentAccessor.getIssueService().getIssue(user, request.issueId).getIssue();
    if (issue == null && request.transition != Transition.CREATE) {
      sendError(resp, HttpServletResponse.SC_GONE, "Issue " + request.issueId + " not found.");
      return;
    }

    switch (request.transition) {
      case CREATE:
        createIssue(request, resp, config);
        break;
      case REOPEN:
        applyTransition(issue, Constants.WORKFLOW_REOPEN_TRANSITION_NAME, true, user, resp);
        break;
      case CLOSE:
        applyTransition(issue, Constants.WORKFLOW_CLOSE_TRANSITION_NAME, true, user, resp);
        break;
      case SUPPRESS:
        applyTransition(issue, Constants.WORKFLOW_SUPPRESS_TRANSITION_NAME, false, user, resp);
        break;
      case UNSUPPRESS:
        applyTransition(issue, Constants.WORKFLOW_REOPEN_TRANSITION_NAME, true, user, resp);
        break;
    }
  }

  ProcessedConfig validateRequest(
      String lgtmSignature, byte[] bytes, HttpServletResponse resp, String configKey)
      throws IOException {

    Config config = Config.get(configKey, transactionTemplate, pluginSettingsFactory);

    // check that plugin has been configured at all
    if (config.getLgtmSecret() == null) {
      sendError(
          resp,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Configuration needed – see documentation.");
      return null;
    }

    if (!Util.signatureIsValid(config.getLgtmSecret(), bytes, lgtmSignature)) {
      sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden.");
      return null;
    }

    ProcessedConfig processedConfig = null;
    try {
      processedConfig = new ProcessedConfig(config);
    } catch (InvalidConfigurationException e) {
      log(e.getMessage(), e);
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid configuration");
    }

    return processedConfig;
  }

  void createIssue(Request request, HttpServletResponse resp, ProcessedConfig config)
      throws IOException {

    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setSummary(request.getSummary());
    issueInputParameters.setDescription(request.getDescription());
    issueInputParameters.setReporterId(config.getUser().getName());
    issueInputParameters.setProjectId(config.getProject().getId());
    issueInputParameters.setIssueTypeId(JiraUtils.getLgtmIssueType().getId());

    issueInputParameters.setApplyDefaultValuesWhenParameterNotProvided(true);

    IssueService.CreateValidationResult createValidationResult =
        ComponentAccessor.getIssueService().validateCreate(config.getUser(), issueInputParameters);

    if (createValidationResult.getErrorCollection().hasAnyErrors()) {
      writeErrors(createValidationResult, resp);
    } else {
      IssueService.IssueResult issueResult =
          ComponentAccessor.getIssueService().create(config.getUser(), createValidationResult);

      if (!issueResult.isValid()) {
        writeErrors(issueResult, resp);
        return;
      }
      Response response = new Response(issueResult.getIssue().getId());
      sendJSON(resp, HttpServletResponse.SC_CREATED, response);
    }
  }

  void applyTransition(
      MutableIssue issue,
      String transitionName,
      boolean skipValidate,
      ApplicationUser user,
      HttpServletResponse resp)
      throws IOException {
    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setRetainExistingValuesWhenParameterNotProvided(true, true);

    JiraWorkflow workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue);
    Collection<ActionDescriptor> candidateActions = workflow.getActionsByName(transitionName);

    Builder optionsBuilder = new TransitionOptions.Builder().skipConditions();

    if (skipValidate) optionsBuilder.skipValidators();
    TransitionOptions transitionOptions = optionsBuilder.build();

    // is there any transition with matching name that is applicable for the current issue status?
    boolean anyApplicable = false;
    boolean success = false;
    for (ActionDescriptor action : candidateActions) {

      IssueService.TransitionValidationResult transitionValidationResult =
          ComponentAccessor.getIssueService()
              .validateTransition(
                  user, issue.getId(), action.getId(), issueInputParameters, transitionOptions);

      if (transitionValidationResult.isValid()) {
        anyApplicable = true;
        IssueService.IssueResult issueResult =
            ComponentAccessor.getIssueService().transition(user, transitionValidationResult);

        if (issueResult.isValid()) {
          success = true;
          break;
        }
      }
    }
    if (!anyApplicable) {
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No valid transition found.");
      return;
    }
    if (!success) {
      // There was an applicable transition, however, it could not be executed.
      // This is expected if a Validator Function rejects the transition. There is no point
      // telling LGTM about this so return 200 OK nevertheless and log a message.
      log(
          String.format(
              "Transition %s could not be applied for issue %s with status %s.",
              transitionName, issue.getId(), issue.getStatus().getName()));
    }
    Response response = new Response(issue.getId());
    sendJSON(resp, HttpServletResponse.SC_OK, response);
  }

  private void writeErrors(ServiceResult result, HttpServletResponse resp) throws IOException {
    String message =
        result.getErrorCollection().getErrors().entrySet().stream()
            .map(x -> x.getKey() + " : " + x.getValue())
            .collect(Collectors.joining(", ", "Invalid configuration – ", ""));
    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, message);
  }

  private void sendError(HttpServletResponse resp, int code, String message) throws IOException {
    sendJSON(resp, code, new JsonError(code, message));
  }

  private void sendJSON(HttpServletResponse resp, int code, Object value) throws IOException {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.setStatus(code);
    Util.JSON.writeValue(resp.getOutputStream(), value);
  }
}
