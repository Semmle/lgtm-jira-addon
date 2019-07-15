package com.semmle.jira.addon.util.workflow;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.config.StatusCategoryManager;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.status.category.StatusCategory;
import com.atlassian.jira.ofbiz.OfBizDelegator;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.ConfigurableJiraWorkflow;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowUtil;
import com.google.common.collect.ImmutableMap;
import com.opensymphony.workflow.FactoryException;
import com.opensymphony.workflow.loader.AbstractDescriptor;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.ConditionDescriptor;
import com.opensymphony.workflow.loader.DescriptorFactory;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.ValidatorDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
import com.semmle.jira.addon.workflow.ResolutionCondition;
import com.semmle.jira.addon.workflow.ResolutionValidator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.codec.digest.DigestUtils;

public class WorkflowUtils {

  private static final String UPDATE_FIELD_FUNCTION_ID =
      com.atlassian.jira.workflow.function.issue.UpdateIssueFieldFunction.class.getName();
  private static final String RESOLUTION_VALIDATOR_FUNCTION_ID =
      ResolutionValidator.class.getName();
  private static final String RESOLUTION_CONDITION_FUNCTION_ID =
      ResolutionCondition.class.getName();

  public static void createWorkflow(
      String workflowName,
      String workflowXml,
      WorkflowStatus[] statuses,
      WorkflowResolution[] resolutions)
      throws FactoryException {
    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    if (workflowManager.workflowExists(workflowName)) {
      return;
    }

    WorkflowDescriptor descriptor = WorkflowUtil.convertXMLtoWorkflowDescriptor(workflowXml);

    updateStatuses(descriptor, statuses);
    updateResolutions(descriptor, resolutions);

    JiraWorkflow workflow = new ConfigurableJiraWorkflow(workflowName, descriptor, workflowManager);
    workflowManager.createWorkflow( // User is not needed for creating
        (ApplicationUser) null, workflow);
  }

  public static void addLayoutToWorkflow(String workflowName, String layoutJson) {
    OfBizDelegator ofBizDelegator = ComponentAccessor.getComponent(OfBizDelegator.class);
    String layoutKey = DigestUtils.md5Hex(workflowName);
    final Map<String, Object> entryFields =
        ImmutableMap.of(
            "entityName",
            "com.atlassian.jira.plugins.jira-workflow-designer",
            "entityId",
            1,
            "propertyKey",
            "jira.workflow.layout.v5:" + layoutKey,
            "type",
            6);
    final Long layoutId = ofBizDelegator.createValue("OSPropertyEntry", entryFields).getLong("id");
    final Map<String, Object> textFields =
        ImmutableMap.of(
            "id", layoutId,
            "value", layoutJson);
    ofBizDelegator.createValue("OSPropertyText", textFields);
  }

  public static void addDefaultPriorityToWorkflow(String workflowName, Priority priority) {
    FunctionDescriptor setPriorityFunction =
        DescriptorFactory.getFactory().createFunctionDescriptor();
    setPriorityFunction.setType("class");
    setPriorityFunction.setName("Priority");

    Map<String, String> args = setPriorityFunction.getArgs();
    args.put(
        "full.module.key", "com.atlassian.jira.plugin.system.workflowupdate-issue-field-function");
    args.put("field.name", "priority");
    args.put("field.value", priority.getId());
    args.put("class.name", "com.atlassian.jira.workflow.function.issue.UpdateIssueFieldFunction");

    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    JiraWorkflow workflow = workflowManager.getWorkflow(workflowName);

    List<ActionDescriptor> initialActions = workflow.getDescriptor().getInitialActions();
    for (ActionDescriptor action : initialActions) {
      addFunctionAsFirst(action, setPriorityFunction);
    }
  }

  private static void updateStatuses(WorkflowDescriptor descriptor, WorkflowStatus[] statusesJson) {
    Map<String, WorkflowStatus> statuses = mapStatuses(statusesJson);

    List<StepDescriptor> steps = descriptor.getSteps();
    for (StepDescriptor step : steps) {
      Map<String, String> meta = step.getMetaAttributes();
      String statusName = step.getName();
      WorkflowStatus workflowStatus = statuses.get(statusName);

      Status status = null;
      // statusesJson does not include all status
      // not included statuses should be default ones (Open, Closed)
      if (workflowStatus == null) {
        status = getOrCreateStatus(statusName, "", 0);
      } else {
        status =
            getOrCreateStatus(
                statusName,
                workflowStatus.description,
                Long.parseLong(workflowStatus.statusCategoryId));
      }
      meta.put("jira.status.id", status.getId());
    }
  }

  private static void updateResolutions(
      WorkflowDescriptor descriptor, WorkflowResolution[] resolutions) {

    ResolutionManager resolutionManager = ComponentAccessor.getComponent(ResolutionManager.class);
    Map<String, String> existing =
        resolutionManager.getResolutions().stream()
            .collect(Collectors.toMap(Resolution::getName, Resolution::getId));

    // Insert some aliases to try if the preferred resolutions don't exist
    existing.computeIfAbsent("Done", x -> existing.get("Fixed"));
    existing.computeIfAbsent("Won't Do", x -> existing.get("Won't Fix"));

    Map<String, String> resolutionsMap = new LinkedHashMap<>();
    for (WorkflowResolution resolution : resolutions) {
      String newId = existing.get(resolution.name);
      if (newId == null) {
        newId = resolutionManager.createResolution(resolution.name, resolution.description).getId();
      }
      resolutionsMap.put(resolution.originalId, newId);
    }

    List<ActionDescriptor> actions = new ArrayList<>();
    actions.addAll(descriptor.getInitialActions());
    actions.addAll(descriptor.getGlobalActions());
    List<StepDescriptor> steps = descriptor.getSteps();
    for (StepDescriptor step : steps) {
      actions.addAll(step.getActions());
    }

    for (ActionDescriptor action : actions) {
      List<AbstractDescriptor> functions = new ArrayList<>();
      functions.addAll(action.getValidators());
      functions.addAll(action.getPreFunctions());
      functions.addAll(action.getPostFunctions());
      if (action.getUnconditionalResult() != null) {
        functions.addAll(action.getUnconditionalResult().getValidators());
        functions.addAll(action.getUnconditionalResult().getPreFunctions());
        functions.addAll(action.getUnconditionalResult().getPostFunctions());
      }
      if (action.getRestriction() != null
          && action.getRestriction().getConditionsDescriptor() != null) {
        functions.addAll(action.getRestriction().getConditionsDescriptor().getConditions());
      }
      for (AbstractDescriptor function : functions) {
        updateResolutions(function, resolutionsMap);
      }
    }
  }

  private static void updateResolutions(
      AbstractDescriptor function, Map<String, String> resolutionsMap) {

    String type;
    Map<String, String> args;
    if (function instanceof ConditionDescriptor) {
      type = ((ConditionDescriptor) function).getType();
      args = ((ConditionDescriptor) function).getArgs();
    } else if (function instanceof ValidatorDescriptor) {
      type = ((ValidatorDescriptor) function).getType();
      args = ((ValidatorDescriptor) function).getArgs();
    } else if (function instanceof FunctionDescriptor) {
      type = ((FunctionDescriptor) function).getType();
      args = ((FunctionDescriptor) function).getArgs();
    } else {
      return;
    }
    // skip non-class functions
    if (!"class".equals(type)) return;

    Object name = args.get("class.name");
    if (UPDATE_FIELD_FUNCTION_ID.equals(name)) {
      if ("resolution".equals(args.get("field.name"))) {
        String oldValue = args.get("field.value");
        String newValue = resolutionsMap.get(oldValue);
        if (newValue != null) args.put("field.value", newValue);
      }
    } else if (RESOLUTION_VALIDATOR_FUNCTION_ID.equals(name)
        || RESOLUTION_CONDITION_FUNCTION_ID.equals(name)) {
      String oldValue = args.get("resolution");
      String newValue = resolutionsMap.get(oldValue);
      if (newValue != null) args.put("resolution", newValue);
    }
  }

  private static void addFunctionAsFirst(ActionDescriptor transition, FunctionDescriptor function) {
    List<FunctionDescriptor> functions = transition.getUnconditionalResult().getPostFunctions();
    // remove existing functions with the same name
    String name = function.getName();
    if (name != null) {
      Iterator<?> iter = functions.iterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof FunctionDescriptor) {
          FunctionDescriptor fun = (FunctionDescriptor) obj;
          if (name.equals(fun.getName())) {
            iter.remove();
          }
        }
      }
    }
    functions.add(0, function);
  }

  private static Status getOrCreateStatus(
      String statusName, String description, long statusCategoryId) {
    StatusManager statusManager = ComponentAccessor.getComponent(StatusManager.class);
    Collection<Status> statuses = statusManager.getStatuses();
    for (Status status : statuses) {
      if (status.getName().equalsIgnoreCase(statusName)) {
        return status;
      }
    }
    StatusCategoryManager statusCategoryManager =
        ComponentAccessor.getComponent(StatusCategoryManager.class);
    StatusCategory category = statusCategoryManager.getStatusCategory(statusCategoryId);
    if (category == null) category = statusCategoryManager.getDefaultStatusCategory();
    return statusManager.createStatus(statusName, description, "status.png", category);
  }

  private static Map<String, WorkflowStatus> mapStatuses(WorkflowStatus[] statuses) {

    Map<String, WorkflowStatus> statusesMap = new LinkedHashMap<>();
    for (WorkflowStatus status : statuses) {
      statusesMap.put(status.name, status);
    }

    return statusesMap;
  }
}
