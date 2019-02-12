package com.semmle.jira.addon;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.bc.issue.IssueService.TransitionValidationResult;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.jira.util.SimpleErrorCollection;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.semmle.jira.addon.config.ProcessedConfig;

public class TestApplyTransition extends TestCreateAndTransitionBase {

  @AvailableInContainer protected WorkflowManager workflowManager = mock(WorkflowManager.class);

  TransitionValidationResult transitionValidationResult = mock(TransitionValidationResult.class);

  Status currentStatus = mock(Status.class);
  Status targetStatus = mock(Status.class);
  JiraWorkflow workflow = mock(JiraWorkflow.class);

  StepDescriptor currentStep = mock(StepDescriptor.class);
  int targetStepId = 2;

  ActionDescriptor action = mock(ActionDescriptor.class);

  ProcessedConfig config = mock(ProcessedConfig.class);

  @Before
  public void initTests() {
    super.initTests();

    when(issueService.validateTransition(
            any(ApplicationUser.class), any(Long.class), anyInt(), any(IssueInputParameters.class)))
        .thenReturn(transitionValidationResult);

    IssueResult issueResult = mock(IssueResult.class);
    when(issueService.getIssue(any(), any(Long.class))).thenReturn(issueResult);

    MutableIssue issue = mock(MutableIssue.class);
    when(issue.getStatus()).thenReturn(currentStatus);
    when(issueService.getIssue(any(), any(Long.class)).getIssue()).thenReturn(issue);

    when(workflowManager.getWorkflow(any(MutableIssue.class))).thenReturn(workflow);

    // currentStep and targetStepId defined in the class so testApplyTransitionAlreadyInTargetStatus
    // can use them
    int currentStepId = 1;
    when(workflow.getLinkedStep(eq(currentStatus))).thenReturn(currentStep);
    when(currentStep.getId()).thenReturn(currentStepId);

    StepDescriptor targetStep = mock(StepDescriptor.class);
    when(workflow.getLinkedStep(eq(targetStatus))).thenReturn(targetStep);
    when(targetStep.getId()).thenReturn(targetStepId);

    Collection<ActionDescriptor> actionDescriptors = Collections.singletonList(action);
    when(workflow.getActionsWithResult(any())).thenReturn(actionDescriptors);
    AbstractDescriptor abstractDescriptor = mock(AbstractDescriptor.class);
    when(action.getParent()).thenReturn(abstractDescriptor);
    when(action.getParent().getId()).thenReturn(1);

    ErrorCollection errorCollection = mock(ErrorCollection.class);
    when(transitionValidationResult.getErrorCollection()).thenReturn(errorCollection);

    ApplicationUser user = mock(ApplicationUser.class);
    when(config.getUser()).thenReturn(user);
  }

  @Test
  public void testApplyTransitionIssueNotFound() throws IOException {
    Long issueId = 1l;
    HttpServletResponse resp = mockResponse();

    when(issueService.getIssue(any(), any(Long.class)).getIssue())
        .thenReturn(null); // Mock that the issue not exists

    servlet.applyTransition(issueId, resp, targetStatus, config);

    verify(resp.getWriter()).write("{\"code\":404,\"error\":\"Issue " + issueId + " not found.\"}");
    verify(resp).setStatus(404);
  }

  @Test
  public void testApplyTransitionAlreadyInTargetStatus() throws IOException {
    Long issueId = 1l;
    HttpServletResponse resp = mockResponse();

    when(currentStep.getId()).thenReturn(targetStepId);

    servlet.applyTransition(issueId, resp, targetStatus, config);

    verify(resp).setStatus(200);
  }

  @Test
  public void testApplyTransitionTransitionNotFound() throws IOException {
    Long issueId = 1l;
    HttpServletResponse resp = mockResponse();

    when(transitionValidationResult.isValid())
        .thenReturn(false); // Mock there is no valid transition
    when(currentStatus.getId()).thenReturn("1");
    when(targetStatus.getId()).thenReturn("2");

    servlet.applyTransition(issueId, resp, targetStatus, config);

    verify(resp.getWriter()).write("{\"code\":500,\"error\":\"No valid transition found.\"}");
    verify(resp).setStatus(500);
  }

  @Test
  public void testApplyTransitionFailure() throws IOException {
    Long issueId = 1l;
    HttpServletResponse resp = mockResponse();

    when(transitionValidationResult.isValid()).thenReturn(true);
    when(currentStatus.getId()).thenReturn("1");
    when(targetStatus.getId()).thenReturn("2");

    IssueResult issueResult = mock(IssueResult.class);
    when(issueService.transition(any(), any())).thenReturn(issueResult);
    when(issueResult.isValid()).thenReturn(false);
    when(issueResult.getErrorCollection()).thenReturn(new SimpleErrorCollection());

    servlet.applyTransition(issueId, resp, targetStatus, config);

    verify(resp).setStatus(500);
  }

  @Test
  public void testApplyTransitionSuccess() throws IOException {
    Long issueId = 1l;
    HttpServletResponse resp = mockResponse();

    when(transitionValidationResult.getErrorCollection().hasAnyErrors()).thenReturn(false);

    IssueResult issueResult = mock(IssueResult.class);
    when(issueService.transition(any(), any())).thenReturn(issueResult);
    MutableIssue issue = mock(MutableIssue.class);
    when(issueResult.getIssue()).thenReturn(issue);

    servlet.applyTransition(issueId, resp, targetStatus, config);

    verify(resp).setStatus(200);
  }
}
