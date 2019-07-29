package com.semmle.jira.addon.workflow;

import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService;
import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService.CreateValidationResult;
import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService.RemoteIssueLinkResult;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.link.RemoteIssueLink;
import com.atlassian.jira.issue.link.RemoteIssueLinkBuilder;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import java.util.Map;
import org.codehaus.jackson.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LgtmIssueLinkFunction extends AbstractJiraFunctionProvider {

  private static final Logger log = LoggerFactory.getLogger(LgtmIssueLinkFunction.class);

  @Override
  @SuppressWarnings("rawtypes")
  public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {

    if (!ComponentAccessor.getIssueLinkManager().isLinkingEnabled()) {
      log.debug(
          "LgtmIssueLinkFunction: Remote issue link creation skipped, because issue linking is disabled.");
      return;
    }

    Object object = transientVars.get("issueProperties");
    if (object instanceof Map) {
      object = ((Map) object).get(Constants.LGTM_PAYLOAD_PROPERTY);
      if (object instanceof JsonNode) {
        JsonNode payload = (JsonNode) object;
        Request request;
        try {
          request = Util.JSON.treeToValue(payload, Request.class);
        } catch (IOException e) {
          throw new WorkflowException("Could not read LGTM JSON payload", e);
        }
        if (request.transition != Transition.CREATE)
          throw new WorkflowException(
              LgtmIssueLinkFunction.class.getName() + " should only be used for issue creation.");

        ApplicationUser user = getCallerUser(transientVars, args);
        MutableIssue issue = getIssue(transientVars);
        if (ComponentAccessor.getPermissionManager()
            .hasPermission(ProjectPermissions.LINK_ISSUES, issue.getProjectObject(), user)) {
          createRemoteLink(user, issue, request);
        } else {
          log.debug(
              "LgtmIssueLinkFunction: Remote issue link creation skipped, because user has no LINK_ISSUES permission.");
        }
      }
    }
  }

  private static void createRemoteLink(ApplicationUser user, Issue issue, Request request)
      throws WorkflowException {
    String url = request.alert.url;
    String title = request.alert.query.getName();

    RemoteIssueLinkService issueLinkService =
        ComponentAccessor.getComponent(RemoteIssueLinkService.class);
    RemoteIssueLink link =
        new RemoteIssueLinkBuilder()
            .issueId(issue.getId())
            .relationship("causes")
            .url(url)
            .title(title)
            .applicationName("LGTM")
            .applicationType("com.lgtm")
            .build();
    CreateValidationResult linkValidation = issueLinkService.validateCreate(user, link);
    if (!linkValidation.isValid()) {
      throw new WorkflowException(
          "Could not create remote link due to: "
              + linkValidation.getErrorCollection().getErrorMessages());
    }
    RemoteIssueLinkResult linkResult = issueLinkService.create(user, linkValidation);
    if (!linkResult.isValid()) {
      throw new WorkflowException(
          "Could not create remote link due to: "
              + linkResult.getErrorCollection().getErrorMessages());
    }
  }
}
