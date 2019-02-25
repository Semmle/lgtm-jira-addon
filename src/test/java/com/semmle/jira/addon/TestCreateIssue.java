package com.semmle.jira.addon;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import com.atlassian.jira.bc.issue.IssueService.CreateValidationResult;
import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.label.LabelManager;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.semmle.jira.addon.Utils.Util;
import com.semmle.jira.addon.config.ProcessedConfig;
import com.semmle.jira.addon.util.Constants;

public class TestCreateIssue extends TestCreateAndTransitionBase {

  @AvailableInContainer protected LabelManager labelManager = mock(LabelManager.class);
  @AvailableInContainer private ConstantsManager constantsManager = mock(ConstantsManager.class);

  CreateValidationResult createValidationResult = mock(CreateValidationResult.class);
  ProcessedConfig config = mock(ProcessedConfig.class);

  @Before
  public void initTests() {
    super.initTests();
    
    List<IssueType> allIssueTypes = new ArrayList<IssueType>();
    IssueType lgtmIssueType = mock(IssueType.class);
    when(lgtmIssueType.getName()).thenReturn(Constants.issueTypeName);
    when(lgtmIssueType.getId()).thenReturn("1");
	allIssueTypes.add(lgtmIssueType );
    when(constantsManager.getAllIssueTypeObjects()).thenReturn(allIssueTypes);

    when(issueService.validateCreate(any(ApplicationUser.class), any(IssueInputParameters.class)))
        .thenReturn(createValidationResult);

    ErrorCollection errorCollection = mock(ErrorCollection.class);
    when(createValidationResult.getErrorCollection()).thenReturn(errorCollection);

    IssueResult issueResult = mock(IssueResult.class);
    when(issueService.create(any(), any())).thenReturn(issueResult);
    MutableIssue issue = mock(MutableIssue.class);
    when(issueResult.getIssue()).thenReturn(issue);

    ApplicationUser user = mock(ApplicationUser.class);
    when(config.getUser()).thenReturn(user);
    com.atlassian.jira.project.Project project = mock(com.atlassian.jira.project.Project.class);
    when(config.getProject()).thenReturn(project);
  }

  @Test
  public void testCreateIssueSuccess() throws IOException {
    HttpServletResponse resp = mockResponse();
    Request request = Util.createRequest("test", "Query", "test.cpp", "Security Error");

    when(createValidationResult.getErrorCollection().hasAnyErrors()).thenReturn(false);

    servlet.createIssue(request, resp, config);
    verify(resp).setStatus(201);
  }

  @Test
  public void testCreateIssueFailure() throws IOException {
    HttpServletResponse resp = mockResponse();
    Request request = Util.createRequest("test", "Query", "test.cpp", "Security Error");

    when(createValidationResult.getErrorCollection().hasAnyErrors()).thenReturn(true);

    servlet.createIssue(request, resp, config);
    verify(resp).setStatus(500);
  }
}
