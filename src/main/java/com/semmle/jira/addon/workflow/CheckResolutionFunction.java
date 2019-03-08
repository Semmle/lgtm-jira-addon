package com.semmle.jira.addon.workflow;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import java.util.Map;

@SuppressWarnings("rawtypes")
class CheckResolutionFunction extends AbstractJiraFunctionProvider {
  public static final String FIELD_RESOLUTION = "resolution";
  public static final String FIELD_OPERATOR = "operator";

  @Override
  public void execute(Map transientVars, Map args, PropertySet ps) throws InvalidInputException {
    String resolutionId = (String) args.get(FIELD_RESOLUTION);
    ComparisonOperator operator = ComparisonOperator.valueOf((String) args.get(FIELD_OPERATOR));

    Issue issue = getIssue(transientVars);

    if (!operator.test(resolutionId, issue.getResolutionId())) {
      Resolution resolution =
          ComponentAccessor.getComponent(ResolutionManager.class).getResolution(resolutionId);
      throw new InvalidInputException(
          String.format(
              "The ticket's resolution %s %s",
              operator.negate(), (resolution == null ? "{invalid}" : resolution.getName())));
    }
  }
}
