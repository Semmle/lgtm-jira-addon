package com.semmle.jira.addon;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.bc.issue.IssueService.TransitionValidationResult;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.status.MockStatus;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.TransitionOptions;
import com.atlassian.jira.workflow.WorkflowManager;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.semmle.jira.addon.config.ProcessedConfig;
import com.semmle.jira.addon.util.Constants;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestApplyTransition extends TestCreateAndTransitionBase {

  @AvailableInContainer protected WorkflowManager workflowManager = mock(WorkflowManager.class);

  TransitionValidationResult transitionValidationResult = mock(TransitionValidationResult.class);

  ProcessedConfig config = mock(ProcessedConfig.class);

  @Before
  public void initTests() {
    super.initTests();

    when(issueService.validateTransition(
            any(ApplicationUser.class),
            any(Long.class),
            anyInt(),
            any(IssueInputParameters.class),
            any(TransitionOptions.class)))
        .thenReturn(transitionValidationResult);

    JiraWorkflow workflow = mock(JiraWorkflow.class);
    when(workflowManager.getWorkflow(any(MutableIssue.class))).thenReturn(workflow);

    Collection<ActionDescriptor> actions = Collections.singletonList(mock(ActionDescriptor.class));
    when(workflow.getActionsByName(any(String.class))).thenReturn(actions);

    ApplicationUser user = mock(ApplicationUser.class);
    when(config.getUser()).thenReturn(user);
  }

  @Test
  public void testApplyTransitionTransitionNotFound() throws IOException {
    HttpServletResponse resp = mockResponse();

    when(transitionValidationResult.isValid()).thenReturn(false);

    MutableIssue issue = mock(MutableIssue.class);

    servlet.applyTransition(
        issue, Constants.WORKFLOW_CLOSE_TRANSITION_NAME, true, config.getUser(), resp);

    Assert.assertEquals(
        "{\"code\":500,\"error\":\"No valid transition found.\"}",
        resp.getOutputStream().toString());
    verify(resp).setStatus(500);
  }

  @Test
  public void testApplyTransitionInvalid() throws IOException {
    HttpServletResponse resp = mockResponse();

    when(transitionValidationResult.isValid()).thenReturn(true);

    IssueResult issueResult = mock(IssueResult.class);
    when(issueService.transition(any(), any())).thenReturn(issueResult);
    when(issueResult.isValid()).thenReturn(false);
    when(issueResult.getErrorCollection()).thenReturn(new SimpleErrorCollection());

    MutableIssue issue = mock(MutableIssue.class);
    when(issue.getStatus()).thenReturn(new MockStatus("100", "StatusName"));

    servlet.applyTransition(
        issue, Constants.WORKFLOW_CLOSE_TRANSITION_NAME, true, config.getUser(), resp);
    // There was an applicable transition, however, it could not be executed. We just log a message
    // if this happens.
    Assert.assertEquals(
        "Transition LGTM.Close could not be applied for issue 0 with status StatusName.",
        log.get(0));
    verify(resp).setStatus(200);
  }

  @Test
  public void testApplyTransitionSuccess() throws IOException {
    HttpServletResponse resp = mockResponse();

    when(transitionValidationResult.isValid()).thenReturn(true);

    IssueResult issueResult = mock(IssueResult.class);
    when(issueService.transition(any(), any())).thenReturn(issueResult);
    when(issueResult.isValid()).thenReturn(true);

    MutableIssue issue = mock(MutableIssue.class);
    when(issueResult.getIssue()).thenReturn(issue);

    servlet.applyTransition(
        issue, Constants.WORKFLOW_CLOSE_TRANSITION_NAME, true, config.getUser(), resp);

    verify(resp).setStatus(200);
  }
}
