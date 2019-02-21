package com.semmle.jira.addon.util;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.ofbiz.core.entity.GenericEntityException;
import org.ofbiz.core.entity.GenericValue;

public class JiraUtils {

  public static void addIssueTypeToProject(String projectKey, String issueTypeName)
      throws ProjectNotFoundException, IssueTypeNotFoundException {
    IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();
    Project project = ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
    if (project == null) {
      throw new ProjectNotFoundException();
    }

    Collection<IssueType> currentIssueTypes =
        issueTypeSchemeManager.getIssueTypesForProject(project);
    Set<String> issueTypeIds = new HashSet<String>();
    for (IssueType issueType : currentIssueTypes) {
      issueTypeIds.add(issueType.getId());
    }
    IssueType lgtmIssueType = JiraUtils.getIssueTypeByName(issueTypeName);
    if (lgtmIssueType == null) {
      throw new IssueTypeNotFoundException();
    }
    issueTypeIds.add(lgtmIssueType.getId());

    FieldConfigScheme issueTypeScheme = issueTypeSchemeManager.getConfigScheme(project);
    issueTypeSchemeManager.update(issueTypeScheme, issueTypeIds);
  }

  public static IssueType getIssueTypeByName(String issueTypeName) {
    Collection<IssueType> allIssueTypes =
        ComponentAccessor.getConstantsManager().getAllIssueTypeObjects();
    for (IssueType issueType : allIssueTypes) {
      if (issueType.getName().equalsIgnoreCase(issueTypeName)) {
        return issueType;
      }
    }
    return null;
  }

  public static void addWorkflowToProject(String projectKey, String workflowName, String issueTypeName)
      throws GenericEntityException, ProjectNotFoundException, IssueTypeNotFoundException {
    WorkflowSchemeManager workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
    Project project = ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
    if (project == null) {
      throw new ProjectNotFoundException();
    }

    GenericValue workflowScheme = workflowSchemeManager.getWorkflowScheme(project);
    IssueType lgtmIssueType = getIssueTypeByName(issueTypeName);
    if (lgtmIssueType == null) {
      throw new IssueTypeNotFoundException();
    }
    workflowSchemeManager.addWorkflowToScheme(
        workflowScheme, workflowName, lgtmIssueType.getId());
  }
}
