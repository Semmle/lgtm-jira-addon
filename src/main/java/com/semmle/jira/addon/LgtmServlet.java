package com.semmle.jira.addon;

import com.atlassian.jira.bc.ServiceResult;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.LabelsField;
import com.atlassian.jira.issue.label.LabelParser;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.jira.workflow.TransitionOptions.Builder;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.config.InvalidConfigurationException;
import com.semmle.jira.addon.config.ProcessedConfig;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.CustomFieldRetrievalException;
import com.semmle.jira.addon.util.JiraUtils;
import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LgtmServlet extends HttpServlet {
  private static final long serialVersionUID = -1528900525848515993L;
  private static final Logger log = LoggerFactory.getLogger(LgtmServlet.class);

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
    JsonNode jsonRequest;
    try {
      jsonRequest = Util.JSON.readTree(bytes);
      request = Util.JSON.readValue(jsonRequest, Request.class);
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

    try {
      switch (request.transition) {
        case CREATE:
          createIssue(jsonRequest, request, resp, config);
          break;
        case REOPEN:
          applyTransition(issue, Constants.WORKFLOW_REOPEN_TRANSITION_NAME, true, user);
          break;
        case CLOSE:
          applyTransition(issue, Constants.WORKFLOW_CLOSE_TRANSITION_NAME, true, user);
          break;
        case SUPPRESS:
          applyTransition(issue, Constants.WORKFLOW_SUPPRESS_TRANSITION_NAME, false, user);
          break;
        case UNSUPPRESS:
          applyTransition(issue, Constants.WORKFLOW_REOPEN_TRANSITION_NAME, true, user);
          break;
      }
      Response response = new Response(issue.getId());
      sendJSON(resp, HttpServletResponse.SC_OK, response);
    } catch (TransitionNotFoundException e) {
      sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No valid transition found.");
    }
  }

  ProcessedConfig validateRequest(
      String lgtmSignature, byte[] bytes, HttpServletResponse resp, String configKey)
      throws IOException {

    Config config = Config.get(configKey);

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

  void createIssue(
      JsonNode rawRequest, Request request, HttpServletResponse resp, ProcessedConfig config)
      throws IOException {

    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setSummary(request.getSummary());
    issueInputParameters.setDescription(request.getDescription());
    issueInputParameters.setReporterId(config.getUser().getName());
    issueInputParameters.setProjectId(config.getProject().getId());
    issueInputParameters.setIssueTypeId(JiraUtils.getLgtmIssueType().getId());

    issueInputParameters.addProperty(Constants.LGTM_PAYLOAD_PROPERTY, rawRequest);

    issueInputParameters.setApplyDefaultValuesWhenParameterNotProvided(true);

    IssueService.CreateValidationResult createValidationResult =
        ComponentAccessor.getIssueService().validateCreate(config.getUser(), issueInputParameters);

    if (createValidationResult.getErrorCollection().hasAnyErrors()) {
      writeErrors(createValidationResult, resp);
      return;
    }

    CustomField customField;
    try {
      customField = JiraUtils.getConfigKeyCustomField();
    } catch (CustomFieldRetrievalException e) {
      log.error("Retrieval of custom field for config key failed.", e);
      sendError(
          resp,
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
          "Retrieval of configuration key failed. Please check your add-on configuration.");
      return;
    }

    createValidationResult.getIssue().setCustomFieldValue(customField, config.getKey());

    // replace label separators (spaces) with underscores
    String projectLabel = request.project.name.replace(LabelsField.SEPARATOR_CHAR, "_");
    createValidationResult.getIssue().setLabels(LabelParser.buildFromString(projectLabel));

    IssueService.IssueResult issueResult =
        ComponentAccessor.getIssueService().create(config.getUser(), createValidationResult);

    if (!issueResult.isValid()) {
      writeErrors(issueResult, resp);
      return;
    }

    Response response = new Response(issueResult.getIssue().getId());
    sendJSON(resp, HttpServletResponse.SC_CREATED, response);
  }

  void applyTransition(
      MutableIssue issue, String transitionName, boolean skipValidate, ApplicationUser user)
      throws IOException, TransitionNotFoundException {
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
      throw new TransitionNotFoundException();
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

  class TransitionNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;
  }
}
