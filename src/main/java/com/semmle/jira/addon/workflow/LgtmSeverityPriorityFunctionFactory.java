package com.semmle.jira.addon.workflow;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class LgtmSeverityPriorityFunctionFactory extends AbstractWorkflowPluginFactory
    implements WorkflowPluginFunctionFactory {

  private static final String FIELD_PRIORITY_ERROR = "error";
  private static final String FIELD_PRIORITY_WARNING = "warning";
  private static final String FIELD_PRIORITY_RECOMMENDATION = "recommendation";
  private static final String FIELD_PRIORITIES = "priorities";
  private final Map<String, String> priorities = new LinkedHashMap<>();

  public LgtmSeverityPriorityFunctionFactory() {
    ConstantsManager constants = ComponentAccessor.getComponent(ConstantsManager.class);
    priorities.put("", "Default");
    for (Priority priority : constants.getPriorities()) {
      priorities.put(priority.getId(), priority.getName());
    }
  }

  @Override
  protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {
    velocityParams.put(FIELD_PRIORITY_ERROR, LgtmSeverityPriorityFunction.DEFAULT_PRIORITY);
    velocityParams.put(FIELD_PRIORITY_WARNING, LgtmSeverityPriorityFunction.DEFAULT_PRIORITY);
    velocityParams.put(
        FIELD_PRIORITY_RECOMMENDATION, LgtmSeverityPriorityFunction.DEFAULT_PRIORITY);

    velocityParams.put(FIELD_PRIORITIES, priorities);
  }

  @Override
  protected void getVelocityParamsForEdit(
      Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
    getVelocityParamsForInput(velocityParams);
    getVelocityParamsForView(velocityParams, descriptor);
  }

  @Override
  protected void getVelocityParamsForView(
      Map<String, Object> velocityParams, AbstractDescriptor descriptor) {
    if (!(descriptor instanceof FunctionDescriptor)) {
      throw new IllegalArgumentException("Descriptor must be a FunctionDescriptor.");
    }

    FunctionDescriptor function = (FunctionDescriptor) descriptor;

    for (String field :
        Arrays.asList(
            FIELD_PRIORITY_ERROR, FIELD_PRIORITY_WARNING, FIELD_PRIORITY_RECOMMENDATION)) {

      String priority = (String) function.getArgs().get(field);
      if (priority == null) priority = LgtmSeverityPriorityFunction.DEFAULT_PRIORITY;
      velocityParams.put(field, priority);
    }
  }

  @Override
  public Map<String, String> getDescriptorParams(Map<String, Object> formParams) {
    Map<String, String> params = new LinkedHashMap<>();

    for (String field :
        Arrays.asList(
            FIELD_PRIORITY_ERROR, FIELD_PRIORITY_WARNING, FIELD_PRIORITY_RECOMMENDATION)) {
      String priority = extractSingleParam(formParams, field);
      if (priority == null
          || priority.equals(LgtmSeverityPriorityFunction.DEFAULT_PRIORITY)
          || !priorities.containsKey(priority)) priority = null;

      params.put(field, priority);
    }
    return params;
  }
}
