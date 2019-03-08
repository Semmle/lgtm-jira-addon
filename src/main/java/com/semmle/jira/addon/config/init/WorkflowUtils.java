package com.semmle.jira.addon.config.init;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.codec.digest.DigestUtils;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.config.StatusManager;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.issue.status.category.StatusCategory;
import com.atlassian.jira.issue.status.category.StatusCategoryImpl;
import com.atlassian.jira.ofbiz.OfBizDelegator;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.workflow.ConfigurableJiraWorkflow;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowUtil;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.opensymphony.workflow.FactoryException;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.opensymphony.workflow.loader.StepDescriptor;
import com.opensymphony.workflow.loader.WorkflowDescriptor;

public class WorkflowUtils {

  private static final String UPDATE_FIELD_FUNCTION_ID =
      com.atlassian.jira.workflow.function.issue.UpdateIssueFieldFunction.class.getName();

  static void createWorkflow(
      String workflowName, String workflowXml, String statusesJson, String resolutionsJson)
      throws FactoryException {
    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    if (workflowManager.workflowExists(workflowName)) {
      return;
    }

    WorkflowDescriptor descriptor = WorkflowUtil.convertXMLtoWorkflowDescriptor(workflowXml);

    updateStatuses(descriptor, statusesJson);
    updateResolutions(descriptor, resolutionsJson);

    JiraWorkflow workflow = new ConfigurableJiraWorkflow(workflowName, descriptor, workflowManager);
    workflowManager.createWorkflow( // User is not needed for creating
        (ApplicationUser) null, workflow);
  }

  @SuppressWarnings("unchecked")
  private static void updateStatuses(WorkflowDescriptor descriptor, String statusesJson) {
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

  @SuppressWarnings("unchecked")
  private static void updateResolutions(WorkflowDescriptor descriptor, String resolutionsJson) {
    WorkflowResolution[] resolutions =
        new Gson().fromJson(resolutionsJson, WorkflowResolution[].class);

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
    List<StepDescriptor> steps = descriptor.getSteps();
    for (StepDescriptor step : steps) {

      for (ActionDescriptor action : (List<ActionDescriptor>) step.getActions()) {
        for (FunctionDescriptor function :
            (List<FunctionDescriptor>) action.getUnconditionalResult().getPostFunctions()) {
          // skip non-class functions
          if (!"class".equals(function.getType())) continue;

          Map<String, String> args = function.getArgs();
          Object name = args.get("class.name");
          if (UPDATE_FIELD_FUNCTION_ID.equals(name)) {
            if ("resolution".equals(args.get("field.name"))) {
              String oldValue = (String) args.get("field.value");
              String newValue = resolutionsMap.get(oldValue);
              if (newValue != null) args.put("field.value", newValue);
            }
          }
        }
      }
    }
  }

  static void addLayoutToWorkflow(String workflowName, String layoutJson) {
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

  private static Status getOrCreateStatus(
      String statusName, String description, long statusCategoryId) {
    Status status =
        (Status)
            ComponentAccessor.getConstantsManager()
                .getIssueConstantByName(
                    ConstantsManager.CONSTANT_TYPE.STATUS.getType(), statusName);
    if (status != null) {
      return status;
    }

    StatusManager statusManager = ComponentAccessor.getComponent(StatusManager.class);
    StatusCategory category = StatusCategoryImpl.findById(statusCategoryId);

    return statusManager.createStatus(statusName, description, "status.png", category);
  }

  private static Map<String, WorkflowStatus> mapStatuses(String statusesJson) {
    WorkflowStatus[] statuses = new Gson().fromJson(statusesJson, WorkflowStatus[].class);

    Map<String, WorkflowStatus> statusesMap = new LinkedHashMap<String, WorkflowStatus>();
    for (WorkflowStatus status : statuses) {
      statusesMap.put(status.name, status);
    }

    return statusesMap;
  }
}
