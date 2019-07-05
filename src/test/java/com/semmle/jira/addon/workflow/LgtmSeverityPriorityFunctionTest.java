package com.semmle.jira.addon.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.junit.rules.MockitoContainer;
import com.atlassian.jira.junit.rules.MockitoMocksInContainer;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Alert;
import com.semmle.jira.addon.Request.Alert.Query;
import com.semmle.jira.addon.Request.Alert.Query.Properties;
import com.semmle.jira.addon.Request.Project;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.Util;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.codehaus.jackson.JsonNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

public class LgtmSeverityPriorityFunctionTest {
  @Rule public MockitoContainer mockitoContainer = MockitoMocksInContainer.rule(this);

  private LgtmSeverityPriorityFunction function;

  @Before
  public void setup() {
    function = new LgtmSeverityPriorityFunction();
  }

  @Test
  public void testExecute() throws WorkflowException {
    testExecute("error", Collections.singletonMap("error", "200"), "200");
    testExecute("warning", Collections.singletonMap("warning", "20"), "20");
    testExecute("recommendation", Collections.singletonMap("recommendation", "5"), "5");
    testExecute("error", Collections.singletonMap("warning", ""), null);
    testExecute("recommendation", Collections.singletonMap("recommendation", ""), null);
    testExecute("recommendation", Collections.emptyMap(), null);
    testExecute(null, Collections.emptyMap(), null);
  }

  public void testExecute(String severity, Map<String, String> args, String expected)
      throws WorkflowException {
    Map<String, Object> transientVars = new LinkedHashMap<>();
    Project project =
        new Project(1L, "example/project", "Example project", "http://lgtm.example.com/projects/1");
    Properties properties = severity == null ? null : new Properties(severity);
    Query query = new Query("Query name", properties, "http://lgtm.example.com/rules/1");
    Alert alert = new Alert("file", "some error", "http://lgtm.example.com/issues/...", query);
    Request request = new Request(Transition.CREATE, null, project, alert);
    JsonNode json = Util.JSON.valueToTree(request);

    MutableIssue issue = mock(MutableIssue.class);
    when(issue.getId()).thenReturn(10L);
    transientVars.put("issue", issue);
    transientVars.put(
        "issueProperties", Collections.singletonMap(Constants.LGTM_PAYLOAD_PROPERTY, json));
    function.execute(transientVars, args, null);

    if (expected != null) {
      Mockito.verify(issue).setPriorityId(expected);
    } else {
      Mockito.verify(issue, Mockito.never()).setPriorityId(ArgumentMatchers.any());
    }
  }
}
