package com.semmle.jira.addon.workflow;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import java.util.Map;
import org.codehaus.jackson.JsonNode;

public class LgtmSeverityPriorityFunction extends AbstractJiraFunctionProvider {

  static final String DEFAULT_PRIORITY = "";

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
              LgtmSeverityPriorityFunction.class.getName()
                  + " should only be used for issue creation.");
        if (request.alert == null
            || request.alert.query == null
            || request.alert.query.properties == null) return;

        String severity = request.alert.query.properties.severity;
        String priority = (String) args.get(severity);

        if (priority != null && !priority.equals(DEFAULT_PRIORITY)) {
          MutableIssue issue = getIssue(transientVars);
          issue.setPriorityId(priority);
        }
      }
    }
  }
}
