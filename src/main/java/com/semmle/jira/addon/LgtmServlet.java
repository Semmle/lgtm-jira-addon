package com.semmle.jira.addon;

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
      resp.getWriter().write("{\"code\":400,\"error\":\"Error in JSON Syntax" + message + "\"}");
      resp.setStatus(400);
      return;
    }

    if (!request.isValid()) {
      resp.getWriter().write("{\"code\":400,\"error\":\"Invalid request.\"}");
      resp.setStatus(400);
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
      resp.getWriter()
          .write("{\"code\":500,\"error\":\"Configuration needed – see documentation.\"}");
      resp.setStatus(500);
      return null;
    }

    if (!Util.signatureIsValid(config.getLgtmSecret(), bytes, lgtmSignature)) {
      resp.getWriter().write("{\"code\":403,\"error\":\"Forbidden.\"}");
      resp.setStatus(403);
      return null;
    }

    ProcessedConfig processedConfig = null;
    try {
      processedConfig = new ProcessedConfig(config);
    } catch (InvalidConfigurationException e) {
      resp.getWriter().write("{\"code\":500,\"error\":\"" + e.getUserMessage() + "\"}");
      resp.setStatus(500);
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

      resp.setContentType("application/json");
      resp.getWriter().write(new Gson().toJson(response));
      resp.setStatus(201);
    }
  }

  void applyTransition(
      Long issueId, HttpServletResponse resp, Status targetStatus, ProcessedConfig config)
      throws IOException {

    MutableIssue issue =
        ComponentAccessor.getIssueService().getIssue(config.getUser(), issueId).getIssue();

    if (issue == null) {
      resp.getWriter().write("{\"code\":404,\"error\":\"Issue " + issueId + " not found.\"}");
      resp.setStatus(404);
      return;
    }

    JiraWorkflow workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue);

    int currentStepId = workflow.getLinkedStep(issue.getStatus()).getId();

    if (currentStepId == workflow.getLinkedStep(targetStatus).getId()) {
      resp.setContentType("application/json");
      resp.getWriter().write(new Gson().toJson(new Response(issueId)));
      resp.setStatus(200);
      return;
    }

    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setRetainExistingValuesWhenParameterNotProvided(true, true);

    Collection<ActionDescriptor> candidateActions =
        workflow.getActionsWithResult(workflow.getLinkedStep(targetStatus));

    int actionId = -1;
    for (ActionDescriptor action : candidateActions) {
      if (currentStepId == action.getParent().getId()) {
        actionId = action.getId();
        break;
      }
    }
    if (actionId == -1) {
      resp.getWriter().write("{\"code\":404,\"error\":\"No valid transition found.\"}");
      resp.setStatus(404);
      return;
    }

    IssueService.TransitionValidationResult transitionValidationResult =
        ComponentAccessor.getIssueService()
            .validateTransition(config.getUser(), issueId, actionId, issueInputParameters);

    if (transitionValidationResult.getErrorCollection().hasAnyErrors()) {
      writeErrors(transitionValidationResult, resp);
    } else {
      IssueService.IssueResult issueResult =
          ComponentAccessor.getIssueService()
              .transition(config.getUser(), transitionValidationResult);

      Response response = new Response(issueResult.getIssue().getId());

      resp.setContentType("application/json");
      resp.getWriter().write(new Gson().toJson(response));
      resp.setStatus(200);
    }
  }

  private void writeErrors(ServiceResult result, HttpServletResponse resp) throws IOException {
    String message =
        result.getErrorCollection().getErrors().entrySet().stream()
            .map(x -> x.getKey() + " : " + x.getValue())
            .collect(Collectors.joining(", ", "Invalid configuration – ", ""));

    resp.getWriter().write("{\"code\":500,\"error\":\"" + message + "\"}");
    resp.setStatus(500);
  }
}
