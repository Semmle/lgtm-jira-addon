package com.semmle.jira.addon.workflow;

import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.Validator;
import java.util.Map;

public class ResolutionValidator extends CheckResolutionFunction implements Validator {

  @SuppressWarnings("rawtypes")
  @Override
  public void validate(Map transientVars, Map args, PropertySet ps) throws InvalidInputException {
    execute(transientVars, args, ps);
  }
}
