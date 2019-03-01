package com.semmle.jira.addon.util;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;

public class JiraUtils {

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

  public static void addWorkflowToProject(
      Project project, JiraWorkflow workflow, IssueType issueType)
      throws GenericEntityException, UsedIssueTypeException {
    WorkflowSchemeManager workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
    GenericValue workflowScheme = workflowSchemeManager.getWorkflowScheme(project);
    Map<String, String> workflowMap = workflowSchemeManager.getWorkflowMap(project);
    if (workflowMap.containsKey(issueType.getId())) {
      if (!workflowMap.get(issueType.getId()).equals(workflow.getName())) {
        // Issue type already associated with another workflow
        throw new UsedIssueTypeException();
      }
    } else {
      workflowSchemeManager.addWorkflowToScheme(
          workflowScheme, workflow.getName(), issueType.getId());
    }
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
}
