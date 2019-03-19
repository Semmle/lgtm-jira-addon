package com.semmle.jira.addon.config.upgrades;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.JiraUtils;
import java.util.Collection;
import java.util.Collections;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask4CreateConfigKeyField implements PluginUpgradeTask {

  private static final int BUILD_NUMER = 4;

  @Override
  public Collection<Message> doUpgrade() throws Exception {

    if (JiraUtils.getConfigKeyCustomField() == null) {
      throw new Exception(); // TODO should be a more suitable exception
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
