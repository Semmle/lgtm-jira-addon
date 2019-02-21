package com.semmle.jira.addon.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;

public class JiraUtils {
	
	public static void addIssueTypeToProject(String projectKey, String issueTypeName) throws ProjectNotFoundException, IssueTypeNotFoundException {
		IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();
		Project project = ComponentAccessor.getProjectManager().getProjectByCurrentKey(projectKey);
		if (project == null) {
			throw new ProjectNotFoundException();
		}
		
		Collection<IssueType> currentIssueTypes = issueTypeSchemeManager.getIssueTypesForProject(project);
		Set<String> issueTypeIds = new HashSet<String>();
		for (IssueType issueType: currentIssueTypes) {
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
		Collection<IssueType> allIssueTypes = ComponentAccessor.getConstantsManager().getAllIssueTypeObjects();
		for (IssueType issueType: allIssueTypes) {
			if (issueType.getName().equalsIgnoreCase(issueTypeName)) {
				return issueType; 
			}
		}
		return null;
	}
}
