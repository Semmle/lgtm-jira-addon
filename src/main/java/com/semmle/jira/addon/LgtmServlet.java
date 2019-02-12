package com.semmle.jira.addon;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;

import com.atlassian.jira.bc.ServiceResult;
import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.label.LabelManager;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.config.InvalidConfigurationException;
import com.semmle.jira.addon.config.ProcessedConfig;

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
  protected void doPost(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
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
      request = new Gson().fromJson(new String(bytes, StandardCharsets.UTF_8), Request.class);
    } catch (JsonSyntaxException e) {
      String message = e.getCause() != null ? " - " + e.getCause().getMessage() : "";
      sendError(
          resp, HttpServletResponse.SC_BAD_REQUEST, "Syntax error in request body: " + message);
      return;
    }

    if (!request.isValid()) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, "Invalid request.");
      return;
    }

    ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(config.getUser());
    switch (request.transition) {
      case CREATE:
        createIssue(request, resp, config);
        break;

      case REOPEN:
        applyTransition(request.issueId, resp, config.getReopenedStatus(), config);
        break;

      case CLOSE:
        applyTransition(request.issueId, resp, config.getClosedStatus(), config);
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
    issueInputParameters.setIssueTypeId(config.getTaskIssueType().getId());

    if (config.getPriorityLevel() != null)
      issueInputParameters.setPriorityId(config.getPriorityLevel().getId());
    issueInputParameters.setApplyDefaultValuesWhenParameterNotProvided(true);

    IssueService.CreateValidationResult createValidationResult =
        ComponentAccessor.getIssueService().validateCreate(config.getUser(), issueInputParameters);

    if (createValidationResult.getErrorCollection().hasAnyErrors()) {
      writeErrors(createValidationResult, resp);
    } else {
      IssueService.IssueResult issueResult =
          ComponentAccessor.getIssueService().create(config.getUser(), createValidationResult);
      LabelManager mgr = ComponentAccessor.getComponent(LabelManager.class);
      mgr.addLabel(config.getUser(), issueResult.getIssue().getId(), "LGTM", false);
      mgr.addLabel(config.getUser(), issueResult.getIssue().getId(), request.project.name, false);

      Response response = new Response(issueResult.getIssue().getId());
      sendJSON(resp, HttpServletResponse.SC_CREATED, response);
    }
  }

  void applyTransition(
      Long issueId, HttpServletResponse resp, Status targetStatus, ProcessedConfig config)
      throws IOException {

    MutableIssue issue =
        ComponentAccessor.getIssueService().getIssue(config.getUser(), issueId).getIssue();

    if (issue == null) {
      sendError(resp, HttpServletResponse.SC_NOT_FOUND, "Issue " + issueId + " not found.");
      return;
    }

    if (issue.getStatus().getId() == targetStatus.getId()) {
      Response response = new Response(issue.getId());
      sendJSON(resp, HttpServletResponse.SC_OK, response);
      return;
    }

    JiraWorkflow workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue);

    Collection<ActionDescriptor> candidateActions =
        workflow.getActionsWithResult(workflow.getLinkedStep(targetStatus));

    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setRetainExistingValuesWhenParameterNotProvided(true, true);

    for (ActionDescriptor action : candidateActions) {
      IssueService.TransitionValidationResult transitionValidationResult =
          ComponentAccessor.getIssueService()
              .validateTransition(config.getUser(), issueId, action.getId(), issueInputParameters);
      if (transitionValidationResult.isValid()) {
        IssueService.IssueResult issueResult =
            ComponentAccessor.getIssueService()
                .transition(config.getUser(), transitionValidationResult);

        if (!issueResult.isValid()) {
          writeErrors(issueResult, resp);
          return;
        }
        Response response = new Response(issueResult.getIssue().getId());
        sendJSON(resp, HttpServletResponse.SC_OK, response);
        return;
      }
    }
    sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No valid transition found.");
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
    resp.getWriter().write(new Gson().toJson(value));
  }

  public class JsonError {
    public int code;
    public String error;

    public JsonError(int code, String error) {
      this.code = code;
      this.error = error;
    }
  }
}
