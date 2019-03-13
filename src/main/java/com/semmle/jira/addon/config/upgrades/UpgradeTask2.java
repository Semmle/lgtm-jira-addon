package com.semmle.jira.addon.config.upgrades;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.config.init.WorkflowUtils;
import com.semmle.jira.addon.util.Constants;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask2 extends UpgradeTask {

  public static final int buildNumber = 2;

  @Autowired
  public UpgradeTask2(@ComponentImport final PluginSettingsFactory pluginSettingsFactory) {
    super("0.2.0", pluginSettingsFactory);
  }

  @Override
  public Collection<Message> doUpgrade() throws Exception {
    if (isFreshInstall()) return Collections.emptySet();

    String priority = (String) settings.get("com.lgtm.addon.config.webhook.priorityLevelId");
    settings.remove("com.lgtm.addon.config.webhook.priorityLevelId");

    if (priority != null && !priority.isEmpty()) {
      Priority priorityLevel = ComponentAccessor.getConstantsManager().getPriorityObject(priority);
      if (priorityLevel != null) {
        WorkflowUtils.addDefaultPriorityToWorkflow(Constants.WORKFLOW_NAME, priorityLevel);
      }
    }

    putConfiguredVersion();
    return Collections.emptySet();
  }
}
