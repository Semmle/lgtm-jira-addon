package com.semmle.jira.addon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import org.junit.Before;

public class TestCreateAndTransitionBase extends TestLgtmServletBase {
  @AvailableInContainer protected IssueService issueService = mock(IssueService.class);

  @Before
  public void initTests() {
    IssueInputParameters issueInputParameters = mock(IssueInputParameters.class);
    when(issueService.newIssueInputParameters()).thenReturn(issueInputParameters);
  }
}
