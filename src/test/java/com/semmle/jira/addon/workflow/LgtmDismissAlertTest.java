package com.semmle.jira.addon.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.atlassian.jira.issue.MutableIssue;
import com.opensymphony.workflow.InvalidInputException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;

import junit.framework.Assert;

public class LgtmDismissAlertTest {
  private LgtmDismissAlert function;
  private MutableIssue issue;
  private Request requestBody;
  private URL requestURL;

  @Before
  public void setup() {
    issue = mock(MutableIssue.class);
    when(issue.getId()).thenReturn(10L);

    function =
        new LgtmDismissAlert() {
          @Override
          protected MutableIssue getIssue(@SuppressWarnings("rawtypes") Map transientVars) {
            return issue;
          }

          @Override
          protected void postMessage(URL url, Request request) {
            LgtmDismissAlertTest.this.requestURL = url;
            LgtmDismissAlertTest.this.requestBody = request;
          }
        };
  }

  @Test(expected = InvalidInputException.class)
  public void testNullParameters() throws Exception {
    function.execute(Collections.emptyMap(), Collections.emptyMap(), null);
  }

  @Test
  public void testValidParameters() throws Exception {
    Map<String, String> args = new LinkedHashMap<>();
    args.put(LgtmDismissAlert.FIELD_URL, "https://localhost:8080");
    args.put(LgtmDismissAlert.FIELD_TRANSITION, Transition.SUPPRESS.value);
    function.execute(Collections.emptyMap(), args, null);
    Assert.assertEquals("https://localhost:8080", requestURL.toString());
    Assert.assertEquals(Transition.SUPPRESS, requestBody.transition);
    Assert.assertEquals(Long.valueOf(10), requestBody.issueId);
  }
}
