package com.semmle.jira.addon.workflow;

import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginFunctionFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.semmle.jira.addon.Request.Transition;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class LgtmTransitionNotificationFunctionFactory extends AbstractWorkflowPluginFactory
    implements WorkflowPluginFunctionFactory {

  private static final Transition DEFAULT_TRANSITION = Transition.SUPPRESS;
  private static final String FIELD_TRANSITIONS = "transitions";
  private static final String FIELD_TRANSITION_NAME = "transitionName";

  public LgtmTransitionNotificationFunctionFactory() {}

  @Override
  protected void getVelocityParamsForInput(Map<String, Object> velocityParams) {
    velocityParams.put(
        LgtmTransitionNotificationFunction.FIELD_TRANSITION, DEFAULT_TRANSITION.name());
    velocityParams.put(
        FIELD_TRANSITIONS, Arrays.asList(Transition.SUPPRESS, Transition.UNSUPPRESS));
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

    String transition =
        (String) function.getArgs().get(LgtmTransitionNotificationFunction.FIELD_TRANSITION);
    if (transition == null) transition = DEFAULT_TRANSITION.name();
    velocityParams.put(LgtmTransitionNotificationFunction.FIELD_TRANSITION, transition);
    String transitionName = "invalid transition";
    try {
      if (transition != null) transitionName = Transition.valueOf(transition).toString();
    } catch (IllegalArgumentException e) {
      // ignored
    }
    velocityParams.put(FIELD_TRANSITION_NAME, transitionName);
  }

  @Override
  public Map<String, String> getDescriptorParams(Map<String, Object> formParams) {
    Map<String, String> params = new LinkedHashMap<>();

    String transition =
        extractSingleParam(formParams, LgtmTransitionNotificationFunction.FIELD_TRANSITION);
    if (transition == null) transition = DEFAULT_TRANSITION.name();
    params.put(LgtmTransitionNotificationFunction.FIELD_TRANSITION, transition);

    return params;
  }
}
