package com.semmle.jira.addon.workflow;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.Condition;
import com.opensymphony.workflow.InvalidInputException;
import java.util.Map;

public class ResolutionCondition extends CheckResolutionFunction implements Condition {

  @SuppressWarnings("rawtypes")
  @Override
  public boolean passesCondition(Map transientVars, Map args, PropertySet ps) {
    try {
      execute(transientVars, args, ps);
      return true;
    } catch (InvalidInputException e) {
      return false;
    }
  }
}
