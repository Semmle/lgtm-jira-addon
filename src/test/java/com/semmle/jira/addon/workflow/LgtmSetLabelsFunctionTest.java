package com.semmle.jira.addon.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.label.LabelParser;
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
import com.semmle.jira.addon.workflow.LgtmSetLabelsFunction.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.codehaus.jackson.JsonNode;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

public class LgtmSetLabelsFunctionTest {
  @Rule public MockitoContainer mockitoContainer = MockitoMocksInContainer.rule(this);

  private LgtmSetLabelsFunction function;

  @Before
  public void setup() {
    function = new LgtmSetLabelsFunction();
  }

  @Test
  public void testExecute() throws WorkflowException {
    // set project label
    testExecute("Example project", null, null, EnumSet.of(Field.PROJECT_LABEL), "Example_project");
    // unset project label
    testExecute("Example project", null, null, Collections.emptySet(), "");
    testExecute(
        "Example project",
        "javascript",
        null,
        EnumSet.of(Field.QUERY_LANGUAGE_LABEL),
        "javascript");
    testExecute(
        "Example project",
        null,
        new Properties("query/id", null, null, null),
        EnumSet.of(Field.QUERY_ID_LABEL),
        "query/id");
    testExecute(
        "Example project",
        null,
        new Properties(null, null, null, Arrays.asList("security", "external/cwe")),
        EnumSet.of(Field.QUERY_TAGS_LABEL),
        "security external/cwe");
    testExecute(
        "Example project",
        null,
        new Properties(null, null, null, Collections.emptyList()),
        EnumSet.of(Field.QUERY_TAGS_LABEL),
        "");
    // All fields turned on, but the JSON request has only null values
    testExecute(null, null, null, EnumSet.allOf(Field.class), "");
    // All fields turned on, JSON contains values for all
    testExecute(
        "Example project",
        "java",
        new Properties(
            "query/id", "Query name", "warning", Arrays.asList("security", "external/cwe")),
        EnumSet.allOf(Field.class),
        "Example_project java query/id security external/cwe");
    // All fields turned off, JSON contains values for all
    testExecute(
        "Example project",
        "java",
        new Properties(
            "query/id", "Query name", "warning", Arrays.asList("security", "external/cwe")),
        Collections.emptySet(),
        "");
  }

  public void testExecute(
      String projectName,
      String language,
      Properties properties,
      Set<Field> settings,
      String expected)
      throws WorkflowException {
    Map<String, Object> transientVars = new LinkedHashMap<>();
    Project project =
        new Project(1L, "example/project", projectName, "http://lgtm.example.com/projects/1");
    Query query = new Query("Query name", language, properties, "http://lgtm.example.com/rules/1");
    Alert alert = new Alert("file", "some error", "http://lgtm.example.com/issues/...", query);
    Request request = new Request(Transition.CREATE, null, project, alert);
    JsonNode json = Util.JSON.valueToTree(request);

    MutableIssue issue = mock(MutableIssue.class);
    when(issue.getId()).thenReturn(10L);

    transientVars.put("issue", issue);
    transientVars.put(
        "issueProperties", Collections.singletonMap(Constants.LGTM_PAYLOAD_PROPERTY, json));
    Map<String, String> args = new LinkedHashMap<>();
    for (Field field : Field.values()) {
      args.put(field.id(), Boolean.valueOf(settings.contains(field)).toString());
    }
    function.execute(transientVars, args, null);

    Mockito.verify(issue).setLabels(LabelParser.buildFromString(expected));
  }
}
