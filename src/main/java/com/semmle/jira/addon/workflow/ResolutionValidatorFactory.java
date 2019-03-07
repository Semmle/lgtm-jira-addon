package com.semmle.jira.addon.workflow;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.plugin.workflow.AbstractWorkflowPluginFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginConditionFactory;
import com.atlassian.jira.plugin.workflow.WorkflowPluginValidatorFactory;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.ConditionDescriptor;
import com.opensymphony.workflow.loader.ValidatorDescriptor;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ResolutionValidatorFactory extends AbstractWorkflowPluginFactory
    implements WorkflowPluginValidatorFactory, WorkflowPluginConditionFactory {

  private static final String FIELD_RESOLUTION_NAME = "resolutionName";
  private static final String FIELD_OPERATOR_NAME = "operatorName";
  private static final String FIELD_RESOLUTIONS = "resolutions";
  private static final String FIELD_OPERATORS = "operators";
  private Map<String, String> resolutions = new LinkedHashMap<>();

  public ResolutionValidatorFactory() {
    resolutions =
        ComponentAccessor.getComponent(ResolutionManager.class).getResolutions().stream()
            .collect(Collectors.toMap(Resolution::getId, Resolution::getName));
  }

  @Override
  protected void getVelocityParamsForInput(Map velocityParams) {
    velocityParams.put(FIELD_RESOLUTIONS, resolutions);
    velocityParams.put(FIELD_OPERATORS, Operator.values());
  }

  @Override
  protected void getVelocityParamsForEdit(Map velocityParams, AbstractDescriptor descriptor) {
    getVelocityParamsForInput(velocityParams);
    getVelocityParamsForView(velocityParams, descriptor);
  }

  @Override
  protected void getVelocityParamsForView(Map velocityParams, AbstractDescriptor descriptor) {
    Map<String, String> arguments;
    if (descriptor instanceof ValidatorDescriptor) {
      arguments = ((ValidatorDescriptor) descriptor).getArgs();
    } else if (descriptor instanceof ConditionDescriptor) {
      arguments = ((ConditionDescriptor) descriptor).getArgs();
    } else {
      throw new IllegalArgumentException(
          "Descriptor must be a ConditionDescriptor or ValidatorDescriptor.");
    }
    String resolutionId = arguments.get(ResolutionValidator.FIELD_RESOLUTION);
    String operatorId = arguments.get(ResolutionValidator.FIELD_OPERATOR);
    velocityParams.put(ResolutionValidator.FIELD_RESOLUTION, resolutionId);
    velocityParams.put(ResolutionValidator.FIELD_OPERATOR, operatorId);
    String resolution = resolutions.get(resolutionId);
    velocityParams.put(FIELD_RESOLUTION_NAME, resolution == null ? "invalid" : resolution);
    String operator = "invalid operator";
    try {
      if (operatorId != null) operator = Operator.valueOf(operatorId).toString();
    } catch (IllegalArgumentException e) {
      // ignored
    }
    velocityParams.put(FIELD_OPERATOR_NAME, operator);
  }

  @Override
  public Map<String, String> getDescriptorParams(Map validatorParams) {
    // Process The map
    Map<String, String> params = new LinkedHashMap<>();
    params.put(
        ResolutionValidator.FIELD_RESOLUTION,
        extractSingleParam(validatorParams, ResolutionValidator.FIELD_RESOLUTION));
    params.put(
        ResolutionValidator.FIELD_OPERATOR,
        extractSingleParam(validatorParams, ResolutionValidator.FIELD_OPERATOR));
    return params;
  }
}
