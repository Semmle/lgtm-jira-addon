package com.semmle.jira.addon.config.upgrades;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.managedconfiguration.ConfigurationItemAccessLevel;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItem;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItemBuilder;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItemService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.context.GlobalIssueContext;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.JiraUtils;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask4CreateConfigKeyField implements PluginUpgradeTask {

  private static final int BUILD_NUMER = 4;
  private PluginSettings settings;
  private ManagedConfigurationItemService managedConfigurationItemService;

  @Autowired
  public UpgradeTask4CreateConfigKeyField(
      @ComponentImport final PluginSettingsFactory pluginSettingsFactory,
      @ComponentImport final ManagedConfigurationItemService managedConfigurationItemService) {
    this.settings = pluginSettingsFactory.createGlobalSettings();
    this.managedConfigurationItemService = managedConfigurationItemService;
  }

  @Override
  public Collection<Message> doUpgrade() throws Exception {

    if (settings.get(Constants.CUSTOM_FIELD_CONFIG_KEY) == null) {

      CustomFieldManager customFieldManager = ComponentAccessor.getCustomFieldManager();

      CustomField customField =
          customFieldManager.createCustomField(
              Constants.CUSTOM_FIELD_NAME,
              Constants.CUSTOM_FIELD_NAME,
              customFieldManager.getCustomFieldType(
                  "com.atlassian.jira.plugin.system.customfieldtypes:textfield"),
              customFieldManager.getCustomFieldSearcher(
                  "com.atlassian.jira.plugin.system.customfieldtypes:exacttextsearcher"),
              Collections.singletonList(GlobalIssueContext.getInstance()),
              Collections.singletonList(JiraUtils.getLgtmIssueType()));

      ManagedConfigurationItem managedField;
      ManagedConfigurationItemBuilder builder;

      managedField = managedConfigurationItemService.getManagedCustomField(customField);
      if (managedField != null) {
        builder = ManagedConfigurationItemBuilder.builder(managedField);
        builder.setManaged(true);
        builder.setConfigurationItemAccessLevel(ConfigurationItemAccessLevel.LOCKED);
        managedField = builder.build();
        managedConfigurationItemService.updateManagedConfigurationItem(managedField);
      }

      settings.put(Constants.CUSTOM_FIELD_CONFIG_KEY, customField.getIdAsLong().toString());
    }
    return Collections.emptyList();
  }

  @Override
  public int getBuildNumber() {
    return BUILD_NUMER;
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task "
        + BUILD_NUMER
        + " create '"
        + Constants.CUSTOM_FIELD_NAME
        + "' custom field.";
  }
}
