package com.semmle.jira.addon.util;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.fields.screen.FieldScreen;
import com.atlassian.jira.issue.fields.screen.FieldScreenScheme;
import com.atlassian.jira.issue.fields.screen.FieldScreenTab;
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.operation.IssueOperations;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraUtils {
  private static final Logger log = LoggerFactory.getLogger(JiraUtils.class);

  public static void addIssueTypeToProject(Project project, IssueType newIssueType) {
    IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();

    Collection<IssueType> currentIssueTypes =
        issueTypeSchemeManager.getIssueTypesForProject(project);
    Set<String> issueTypeIds = new LinkedHashSet<String>();
    for (IssueType issueType : currentIssueTypes) {
      issueTypeIds.add(issueType.getId());
    }

    issueTypeIds.add(newIssueType.getId());

    FieldConfigScheme issueTypeScheme = issueTypeSchemeManager.getConfigScheme(project);
    issueTypeSchemeManager.update(issueTypeScheme, issueTypeIds);
  }

  public static IssueType getIssueTypeByName(String issueTypeName) {
    ComponentAccessor.getConstantsManager();
    return (IssueType)
        ComponentAccessor.getConstantsManager()
            .getIssueConstantByName(
                ConstantsManager.CONSTANT_TYPE.ISSUE_TYPE.getType(), issueTypeName);
  }

  public static IssueType getLgtmIssueType() {
    return getIssueTypeByName(Constants.ISSUE_TYPE_NAME);
  }

  public static void configureWorkflowForProject(
      Project project, IssueType issueType, ApplicationUser user)
      throws GenericEntityException, UsedIssueTypeException, WorkflowNotFoundException {

    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    JiraWorkflow currentWorkflow = workflowManager.getWorkflow(project.getId(), issueType.getId());
    // If the currently configured workflow is good then there is no need to change anything.
    if (isCompatible(currentWorkflow)) return;

    JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
    builder.where().project(project.getId()).and().issueType(issueType.getName());
    try {
      SearchResults results =
          ComponentAccessor.getComponent(SearchService.class)
              .searchOverrideSecurity(
                  user, builder.buildQuery(), PagerFilter.newPageAlignedFilter(0, 3));
      if (results.getTotal() != 0) {
        // Issue type already has issues
        throw new UsedIssueTypeException();
      }
    } catch (SearchException e) {
      log.warn("Issue search failed", e);
      // Something failed, better do a manual migration
      throw new UsedIssueTypeException();
    }

    WorkflowSchemeManager workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
    GenericValue workflowScheme = workflowSchemeManager.getWorkflowScheme(project);
    JiraWorkflow workflow = workflowManager.getWorkflow(Constants.WORKFLOW_NAME);
    if (workflow == null) throw new WorkflowNotFoundException();

    workflowSchemeManager.addWorkflowToScheme(
        workflowScheme, workflow.getName(), issueType.getId());
  }

  private static boolean isCompatible(JiraWorkflow workflow) {
    List<String> requiredTransitions =
        Arrays.asList(
            Constants.WORKFLOW_CLOSE_TRANSITION_NAME,
            Constants.WORKFLOW_REOPEN_TRANSITION_NAME,
            Constants.WORKFLOW_SUPPRESS_TRANSITION_NAME);
    for (String transition : requiredTransitions) {
      if (workflow.getActionsByName(transition).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public static Status getLgtmWorkflowStatus(String statusName)
      throws StatusNotFoundException, WorkflowNotFoundException {
    JiraWorkflow workflow = getLgtmWorkflow();

    List<Status> allStatuses = workflow.getLinkedStatusObjects();
    for (Status status : allStatuses) {
      if (status.getName().equals(statusName)) {
        return status;
      }
    }

    throw new StatusNotFoundException();
  }

  public static JiraWorkflow getLgtmWorkflow() throws WorkflowNotFoundException {
    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    JiraWorkflow workflow = workflowManager.getWorkflow(Constants.WORKFLOW_NAME);
    if (workflow == null) {
      throw new WorkflowNotFoundException();
    }
    return workflow;
  }

  public static void createLgtmIssueType() {
    try {
      ComponentAccessor.getComponent(IssueTypeManager.class)
          .createIssueType(Constants.ISSUE_TYPE_NAME, "Issue type for managing LGTM alerts", 0l);
    } catch (IllegalStateException e) {
      // Issue Type already created
    }
  }

  public static void addCustomFieldToProjectCreateScreen(
      Project project, Long configKeyCustomFieldId) {

    CustomField customField =
        ComponentAccessor.getCustomFieldManager()
            .getCustomFieldObject((Long) configKeyCustomFieldId);

    IssueTypeScreenScheme screenScheme =
        ComponentAccessor.getIssueTypeScreenSchemeManager().getIssueTypeScreenScheme(project);

    FieldScreenScheme fieldScreenScheme =
        screenScheme.getEffectiveFieldScreenScheme(getLgtmIssueType());

    FieldScreen screen = fieldScreenScheme.getFieldScreen(IssueOperations.CREATE_ISSUE_OPERATION);

    if (!screen.containsField(customField.getId())) {
      FieldScreenTab firstTab = screen.getTab(0);
      firstTab.addFieldScreenLayoutItem(customField.getId());
    }
  }
}
