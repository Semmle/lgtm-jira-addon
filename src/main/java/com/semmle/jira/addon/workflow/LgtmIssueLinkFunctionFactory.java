package com.semmle.jira.addon.workflow;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import java.util.Collections;
import java.util.Map;

public class LgtmIssueLinkFunctionFactory extends AbstractWorkflowPluginFactory
    implements WorkflowPluginFunctionFactory {

  @Override
  protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {}

  @Override
  protected void getVelocityParamsForEdit(
      Map<String, Object> velocityParams, AbstractDescriptor descriptor) {}

  @Override
  protected void getVelocityParamsForView(
      Map<String, Object> velocityParams, AbstractDescriptor descriptor) {}

  @Override
  public Map<String, ?> getDescriptorParams(Map<String, Object> formParams) {
    return Collections.emptyMap();
  }
}
