package com.semmle.jira.addon.workflow;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.semmle.jira.addon.workflow.LgtmSetLabelsFunction.Field;
import java.util.LinkedHashMap;
import java.util.Map;

public class LgtmSetLabelsFunctionFactory extends AbstractWorkflowPluginFactory
    implements WorkflowPluginFunctionFactory {

  @Override
  protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {
    Map<Field, Boolean> fieldValues = new LinkedHashMap<>();
    for (Field field : Field.values()) {
      fieldValues.put(field, Boolean.FALSE);
    }
    velocityParams.put("fields", fieldValues);
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

    Map<Field, Boolean> fieldValues = new LinkedHashMap<>();
    for (Field field : Field.values()) {
      String value = (String) function.getArgs().get(field.id());
      fieldValues.put(field, Boolean.valueOf(value));
    }
    velocityParams.put("fields", fieldValues);
  }

  @Override
  public Map<String, String> getDescriptorParams(Map<String, Object> formParams) {
    Map<String, String> params = new LinkedHashMap<>();

    for (Field field : Field.values()) {
      String value = null;
      if (formParams.containsKey(field.id())) value = extractSingleParam(formParams, field.id());
      params.put(field.id(), Boolean.valueOf(value).toString());
    }
    return params;
  }
}
