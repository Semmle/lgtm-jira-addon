package com.semmle.jira.addon;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.jira.workflow.TransitionOptions.Builder;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.config.Config.Error;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.CustomFieldRetrievalException;
import com.semmle.jira.addon.util.JiraUtils;
import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import java.security.AccessControlException;
import java.util.Collection;
import java.util.stream.Collectors;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.io.IOUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
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

    Config config = Config.get(configKey);
    Error configError = config.validate();
    if (configError != null) {
      switch (configError) {
        case MISSING_CONFIG_KEY:
          sendError(
              resp,
              HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              "Configuration needed – see documentation.");
          return;
        case MISSING_PROJECT_KEY:
        case MISSING_SECRET:
        case MISSING_USERNAME:
        case PROJECT_NOT_FOUND:
        case USER_NOT_FOUND:
          sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Invalid configuration");
          return;
      }
    }

    Request request;
    try {
      request = validateRequest(req, config.getLgtmSecret());
    } catch (AccessControlException e) {
      sendError(resp, HttpServletResponse.SC_FORBIDDEN, "Forbidden.");
      return;
    } catch (InvalidRequestException e) {
      sendError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
      return;
    }

    ApplicationUser user = config.getUser();
    ComponentAccessor.getJiraAuthenticationContext().setLoggedInUser(user);

    if (request.transition == Request.Transition.CREATE) {
      try {
        Long issueId = createIssue(request, config);
        Response response = new Response(issueId);
        sendJSON(resp, HttpServletResponse.SC_CREATED, response);
      } catch (CustomFieldRetrievalException e) {
        log.error("Retrieval of custom field for config key failed.", e);
        sendError(
            resp,
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
            "Retrieval of configuration key failed. Please check your add-on configuration.");
      } catch (CreateIssueException e) {
        sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
      }
    } else {
      MutableIssue issue =
          ComponentAccessor.getIssueService().getIssue(user, request.issueId).getIssue();
      if (issue == null) {
        sendError(resp, HttpServletResponse.SC_GONE, "Issue " + request.issueId + " not found.");
        return;
      }

      boolean skipValidation = request.transition != Transition.SUPPRESS;
      String transitionName = null;
      switch (request.transition) {
        case REOPEN:
          transitionName = Constants.WORKFLOW_REOPEN_TRANSITION_NAME;
          break;
        case CLOSE:
          transitionName = Constants.WORKFLOW_CLOSE_TRANSITION_NAME;
          break;
        case SUPPRESS:
          transitionName = Constants.WORKFLOW_SUPPRESS_TRANSITION_NAME;
          break;
        case UNSUPPRESS:
          transitionName = Constants.WORKFLOW_REOPEN_TRANSITION_NAME;
          break;
        default:
          throw new RuntimeException("Unexpected transition: " + request.transition);
      }
      try {
        applyTransition(issue, transitionName, skipValidation, user);
        Response response = new Response(issue.getId());
        sendJSON(resp, HttpServletResponse.SC_OK, response);
      } catch (TransitionNotFoundException e) {
        sendError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No valid transition found.");
      }
    }
  }

  Request validateRequest(HttpServletRequest req, String secret)
      throws IOException, AccessControlException, InvalidRequestException {
    byte[] bytes = IOUtils.toByteArray(req.getInputStream());

    if (!Util.signatureIsValid(secret, bytes, req.getHeader("x-lgtm-signature"))) {
      throw new AccessControlException("Forbidden");
    }

    Request request;
    JsonNode jsonRequest;
    try {
      jsonRequest = Util.JSON.readTree(bytes);
      request = new Request(jsonRequest);
    } catch (JsonParseException e) {
      throw new InvalidRequestException("Syntax error in request body: " + e.getMessage());
    }

    if (!request.isValid()) {
      throw new InvalidRequestException("Invalid request.");
    }

    return request;
  }

  Long createIssue(Request request, Config config)
      throws CustomFieldRetrievalException, CreateIssueException {

    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setSummary(request.getSummary());
    issueInputParameters.setDescription(request.getDescription());
    issueInputParameters.setReporterId(config.getUser().getName());
    issueInputParameters.setProjectId(config.getProject().getId());
    issueInputParameters.setIssueTypeId(JiraUtils.getLgtmIssueType().getId());

    issueInputParameters.addProperty(Constants.LGTM_PAYLOAD_PROPERTY, request.jsonRequest);

    issueInputParameters.setApplyDefaultValuesWhenParameterNotProvided(true);

    IssueService.CreateValidationResult createValidationResult =
        ComponentAccessor.getIssueService().validateCreate(config.getUser(), issueInputParameters);

    if (createValidationResult.getErrorCollection().hasAnyErrors()) {
      throw new CreateIssueException(createValidationResult.getErrorCollection());
    }

    CustomField customField = JiraUtils.getConfigKeyCustomField();

    createValidationResult.getIssue().setCustomFieldValue(customField, config.getKey());

    IssueService.IssueResult issueResult =
        ComponentAccessor.getIssueService().create(config.getUser(), createValidationResult);

    if (!issueResult.isValid()) {
      throw new CreateIssueException(issueResult.getErrorCollection());
    }

    return issueResult.getIssue().getId();
  }

  void applyTransition(
      MutableIssue issue, String transitionName, boolean skipValidate, ApplicationUser user)
      throws TransitionNotFoundException {
    IssueInputParameters issueInputParameters =
        ComponentAccessor.getIssueService().newIssueInputParameters();
    issueInputParameters.setRetainExistingValuesWhenParameterNotProvided(true, true);

    JiraWorkflow workflow = ComponentAccessor.getWorkflowManager().getWorkflow(issue);
    Collection<ActionDescriptor> candidateActions = workflow.getActionsByName(transitionName);

    Builder optionsBuilder = new TransitionOptions.Builder().skipConditions();

    if (skipValidate) optionsBuilder.skipValidators();
    TransitionOptions transitionOptions = optionsBuilder.build();

    // is there any transition with matching name that is applicable for the current
    // issue status?
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
      throw new TransitionNotFoundException(transitionName);
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

  private void sendError(HttpServletResponse resp, int code, String message) throws IOException {
    sendJSON(resp, code, new JsonError(code, message));
  }

  private void sendJSON(HttpServletResponse resp, int code, Object value) throws IOException {
    resp.setContentType("application/json");
    resp.setCharacterEncoding("UTF-8");
    resp.setStatus(code);
    Util.JSON.writeValue(resp.getOutputStream(), value);
  }

  static class InvalidRequestException extends Exception {
    private static final long serialVersionUID = 1L;

    public InvalidRequestException(String message) {
      super(message);
    }
  }

  static class TransitionNotFoundException extends Exception {
    private static final long serialVersionUID = 1L;

    public TransitionNotFoundException(String transition) {
      super("Transition '" + transition + "' not found in workflow");
    }
  }

  static class CreateIssueException extends Exception {
    private static final long serialVersionUID = 1L;

    public CreateIssueException(ErrorCollection errorCollection) {
      super(
          "Could not create issue due to: "
              + errorCollection.getErrors().entrySet().stream()
                  .map(x -> x.getKey() + " : " + x.getValue())
                  .collect(Collectors.joining(", ", "Invalid configuration – ", "")));
    }
  }
}
