package com.semmle.jira.addon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.bc.issue.IssueService;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import org.junit.Before;

public class TestCreateAndTransitionBase extends TestLgtmServletBase {
  @AvailableInContainer protected IssueService issueService = mock(IssueService.class);
  protected IssueInputParameters issueInputParameters = mock(IssueInputParameters.class);

  @Before
  public void initTests() {
    when(issueService.newIssueInputParameters()).thenReturn(issueInputParameters);
  }
}
