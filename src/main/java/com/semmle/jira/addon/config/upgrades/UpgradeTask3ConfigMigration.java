package com.semmle.jira.addon.config.upgrades;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.workflow.WorkflowUtils;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask3ConfigMigration implements PluginUpgradeTask {
  private static final String KEY_SETTINGS_NAME = "com.lgtm.addon.config.key";
  public final int BUILD_NUMER = 3;

  protected final PluginSettings settings;

  @Autowired
  public UpgradeTask3ConfigMigration(
      @ComponentImport final PluginSettingsFactory pluginSettingsFactory) {
    this.settings = pluginSettingsFactory.createGlobalSettings();
  }

  @Override
  public Collection<Message> doUpgrade() throws Exception {
    String configKey = (String) settings.get(KEY_SETTINGS_NAME);

    if (configKey != null && !configKey.isEmpty()) {
      // This means we are upgrading from 0.1.0 to 0.2.0
      // We need it because we did not set the configured version previously
      // So this is an alternative but just for the version 0.1.0

      String priority = (String) settings.get("com.lgtm.addon.config.webhook.priorityLevelId");
      settings.remove("com.lgtm.addon.config.webhook.priorityLevelId");

      if (priority != null && !priority.isEmpty()) {
        Priority priorityLevel =
            ComponentAccessor.getConstantsManager().getPriorityObject(priority);
        if (priorityLevel != null) {
          WorkflowUtils.addDefaultPriorityToWorkflow(Constants.WORKFLOW_NAME, priorityLevel);
        }
      }
      settings.put(Constants.CONFIGURED_VERSION_KEY, "0.2.0");
    }

    return Collections.emptySet();
  }

  @Override
  public int getBuildNumber() {
    return BUILD_NUMER;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task " + BUILD_NUMER;
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }
}
