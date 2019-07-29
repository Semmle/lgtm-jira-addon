package com.semmle.jira.addon.workflow;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.LabelsField;
import com.atlassian.jira.issue.label.Label;
import com.atlassian.jira.issue.label.LabelParser;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.codehaus.jackson.JsonNode;

public class LgtmSetLabelsFunction extends AbstractJiraFunctionProvider {

  public static enum Field {
    PROJECT_LABEL("Project"),
    QUERY_ID_LABEL("Query identifier"),
    QUERY_LANGUAGE_LABEL("Query language"),
    QUERY_TAGS_LABEL("Query tags");

    private final String displayName;

    private Field(String displayName) {
      this.displayName = displayName;
    }

    public String id() {
      return name().toLowerCase(Locale.ENGLISH).replace('_', '.');
    }

    public String displayName() {
      return displayName;
    }
  }

  @Override
  @SuppressWarnings("rawtypes")
  public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {

    Object object = transientVars.get("issueProperties");
    if (object instanceof Map) {
      object = ((Map) object).get(Constants.LGTM_PAYLOAD_PROPERTY);

      if (object instanceof JsonNode) {
        JsonNode payload = (JsonNode) object;

        Request request;
        try {
          request = Util.JSON.treeToValue(payload, Request.class);
        } catch (IOException e) {
          throw new WorkflowException("Could not read LGTM JSON payload", e);
        }
        if (request.transition != Transition.CREATE)
          throw new WorkflowException(
              LgtmSetLabelsFunction.class.getName() + " should only be used for issue creation.");

        Set<Label> labels = new LinkedHashSet<>();
        MutableIssue issue = getIssue(transientVars);
        if (issue.getLabels() != null) {
          labels.addAll(issue.getLabels());
        }

        if (request.project != null) {
          Label project = fromString(request.project.name);
          if (project != null && isEnabled(Field.PROJECT_LABEL, args)) {
            labels.add(project);
          }
        }

        if (request.alert != null && request.alert.query != null) {
          Label language = fromString(request.alert.query.language);
          if (language != null && isEnabled(Field.QUERY_LANGUAGE_LABEL, args)) {
            labels.add(language);
          }

          if (request.alert.query.properties != null) {
            Label queryId = fromString(request.alert.query.properties.id);
            if (queryId != null && isEnabled(Field.QUERY_ID_LABEL, args)) {
              labels.add(queryId);
            }

            List<String> tags = request.alert.query.properties.tags;
            if (tags != null && isEnabled(Field.QUERY_TAGS_LABEL, args)) {
              tags.stream().map(LgtmSetLabelsFunction::fromString).forEach(labels::add);
            }
          }
        }
        issue.setLabels(labels);
      }
    }
  }

  private static boolean isEnabled(Field field, Map<String, ?> args) {
    return Boolean.valueOf((String) args.get(field.id()));
  }

  private static Label fromString(String value) {
    if (value == null || value.isEmpty()) return null;
    Set<Label> label = LabelParser.buildFromString(value.replace(LabelsField.SEPARATOR_CHAR, "_"));
    if (label.isEmpty()) return null;
    return label.iterator().next();
  }
}
