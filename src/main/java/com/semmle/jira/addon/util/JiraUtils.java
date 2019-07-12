package com.semmle.jira.addon.util;

import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.config.managedconfiguration.ConfigurationItemAccessLevel;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItem;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItemBuilder;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItemService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.scheme.Scheme;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.workflow.AssignableWorkflowScheme;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.jira.workflow.WorkflowSchemeManager;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraUtils {
  private static final Logger log = LoggerFactory.getLogger(JiraUtils.class);

  public static void addIssueTypeToProject(Project project, IssueType newIssueType) {
    IssueTypeSchemeManager issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager();

    Collection<IssueType> currentIssueTypes =
        issueTypeSchemeManager.getIssueTypesForProject(project);
    Set<String> issueTypeIds = new LinkedHashSet<>();
    for (IssueType issueType : currentIssueTypes) {
      issueTypeIds.add(issueType.getId());
    }

    issueTypeIds.add(newIssueType.getId());

    FieldConfigScheme issueTypeScheme = issueTypeSchemeManager.getConfigScheme(project);
    issueTypeSchemeManager.update(issueTypeScheme, issueTypeIds);
  }

  public static IssueType getIssueTypeByName(String issueTypeName) {
    ComponentAccessor.getConstantsManager();
    return (IssueType)
        ComponentAccessor.getConstantsManager()
            .getIssueConstantByName(
                ConstantsManager.CONSTANT_TYPE.ISSUE_TYPE.getType(), issueTypeName);
  }

  public static IssueType getLgtmIssueType() {
    return getIssueTypeByName(Constants.ISSUE_TYPE_NAME);
  }

  public static void configureWorkflowForProject(
      Project project, IssueType issueType, ApplicationUser user)
      throws UsedIssueTypeException, WorkflowNotFoundException {

    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    JiraWorkflow currentWorkflow = workflowManager.getWorkflow(project.getId(), issueType.getId());
    // If the currently configured workflow is good then there is no need to change anything.
    if (isCompatible(currentWorkflow)) return;

    JqlQueryBuilder builder = JqlQueryBuilder.newBuilder();
    builder.where().project(project.getId()).and().issueType(issueType.getName());
    try {
      SearchResults results =
          ComponentAccessor.getComponent(SearchService.class)
              .searchOverrideSecurity(
                  user, builder.buildQuery(), PagerFilter.newPageAlignedFilter(0, 3));
      if (results.getTotal() != 0) {
        // Issue type already has issues
        throw new UsedIssueTypeException();
      }
    } catch (SearchException e) {
      log.warn("Issue search failed", e);
      // Something failed, better do a manual migration
      throw new UsedIssueTypeException();
    }

    JiraWorkflow workflow = workflowManager.getWorkflow(Constants.WORKFLOW_NAME);
    if (workflow == null) throw new WorkflowNotFoundException();

    WorkflowSchemeManager workflowSchemeManager = ComponentAccessor.getWorkflowSchemeManager();
    AssignableWorkflowScheme workflowScheme = workflowSchemeManager.getWorkflowSchemeObj(project);
    boolean update = !workflowScheme.isDefault() && workflowScheme.getId() != null;
    AssignableWorkflowScheme.Builder schemeBuilder =
        update
            ? workflowScheme.builder()
            : workflowSchemeManager
                .assignableBuilder()
                .setName(project.getKey() + ": LGTM Workflow Scheme");
    schemeBuilder.setMapping(issueType.getId(), workflow.getName());
    workflowScheme = schemeBuilder.build();
    if (update) {
      workflowSchemeManager.updateWorkflowScheme(workflowScheme);
    } else {
      workflowScheme = workflowSchemeManager.createScheme(schemeBuilder.build());
      Scheme scheme = workflowSchemeManager.getSchemeObject(workflowScheme.getId());
      workflowSchemeManager.addSchemeToProject(project, scheme);
    }
  }

  private static boolean isCompatible(JiraWorkflow workflow) {
    List<String> requiredTransitions =
        Arrays.asList(
            Constants.WORKFLOW_CLOSE_TRANSITION_NAME,
            Constants.WORKFLOW_REOPEN_TRANSITION_NAME,
            Constants.WORKFLOW_SUPPRESS_TRANSITION_NAME);
    for (String transition : requiredTransitions) {
      if (workflow.getActionsByName(transition).isEmpty()) {
        return false;
      }
    }
    return true;
  }

  public static Status getLgtmWorkflowStatus(String statusName)
      throws StatusNotFoundException, WorkflowNotFoundException {
    JiraWorkflow workflow = getLgtmWorkflow();

    List<Status> allStatuses = workflow.getLinkedStatusObjects();
    for (Status status : allStatuses) {
      if (status.getName().equals(statusName)) {
        return status;
      }
    }

    throw new StatusNotFoundException();
  }

  public static JiraWorkflow getLgtmWorkflow() throws WorkflowNotFoundException {
    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    JiraWorkflow workflow = workflowManager.getWorkflow(Constants.WORKFLOW_NAME);
    if (workflow == null) {
      throw new WorkflowNotFoundException();
    }
    return workflow;
  }

  public static void createLgtmIssueType() {
    try {
      ComponentAccessor.getComponent(IssueTypeManager.class)
          .createIssueType(Constants.ISSUE_TYPE_NAME, "Issue type for managing LGTM alerts", 0l);
    } catch (IllegalStateException e) {
      // Issue Type already created
    }
  }

  public static void createConfigKeyCustomField() throws GenericEntityException {

    CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();

    ManagedConfigurationItemService managedConfigurationItemService =
        ComponentAccessor.getComponentOfType(ManagedConfigurationItemService.class);

    CustomField customField = null;
    ManagedConfigurationItem managedField;

    String owner = ManagedConfigurationItemService.SOURCE_PREFIX_PLUGIN + Constants.PLUGIN_KEY;
    for (CustomField candidateField :
        customFieldManager.getCustomFieldObjectsByName(Constants.CUSTOM_FIELD_NAME)) {

      managedField = managedConfigurationItemService.getManagedCustomField(candidateField);

      if (managedField != null
          && managedField.isManaged()
          && managedField.getConfigurationItemAccessLevel() == ConfigurationItemAccessLevel.LOCKED
          && managedField.getSourceId().equals(owner)) {
        customField = candidateField;
        break;
      }
    }

    if (customField == null) {

      customField =
          customFieldManager.createCustomField(
              Constants.CUSTOM_FIELD_NAME,
              Constants.CUSTOM_FIELD_NAME,
              customFieldManager.getCustomFieldType(
                  "com.atlassian.jira.plugin.system.customfieldtypes:textfield"),
              customFieldManager.getCustomFieldSearcher(
                  "com.atlassian.jira.plugin.system.customfieldtypes:exacttextsearcher"),
              Collections.singletonList(GlobalIssueContext.getInstance()),
              Collections.singletonList(JiraUtils.getLgtmIssueType()));

      managedField = managedConfigurationItemService.getManagedCustomField(customField);

      ManagedConfigurationItemBuilder builder =
          ManagedConfigurationItemBuilder.builder(managedField);

      builder.setManaged(true);
      builder.setConfigurationItemAccessLevel(ConfigurationItemAccessLevel.LOCKED);
      builder.setSource(owner);

      managedConfigurationItemService.updateManagedConfigurationItem(builder.build());
    }

    getSettingsObject()
        .put(Constants.CUSTOM_FIELD_CONFIG_KEY, customField.getIdAsLong().toString());

    return;
  }

  private static PluginSettings getSettingsObject() {

    PluginSettingsFactory pluginSettingsFactory =
        ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);

    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

    return settings;
  }

  public static CustomField getConfigKeyCustomField() throws CustomFieldRetrievalException {

    Object settingsObject = getSettingsObject().get(Constants.CUSTOM_FIELD_CONFIG_KEY);

    if (settingsObject == null) {
      throw new CustomFieldRetrievalException(
          "Null value found for plugin setting "
              + Constants.CUSTOM_FIELD_CONFIG_KEY
              + ". Indicates that the add-on has not yet been (re)configured.");
    }

    long fieldId;
    try {
      fieldId = Long.parseLong((String) settingsObject);
    } catch (ClassCastException | NumberFormatException e) {
      throw new CustomFieldRetrievalException(
          "Invalid value for plugin setting "
              + Constants.CUSTOM_FIELD_CONFIG_KEY
              + " -> "
              + settingsObject.toString()
              + ". Should be long, corresponding to ID of LGTM custom field.",
          e);
    }

    CustomField customField =
        ComponentAccessor.getCustomFieldManager().getCustomFieldObject(fieldId);

    if (customField == null) {
      throw new CustomFieldRetrievalException(
          "LGTM Custom Field not found for stored id ("
              + String.valueOf(fieldId)
              + "). Field has likely been unlocked and then deleted.");
    }

    return customField;
  }
}
