package com.semmle.jira.addon.config.upgrades;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;
import java.util.Collection;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask1 extends UpgradeTask {
  private static final String KEY_SETTINGS_NAME = "com.lgtm.addon.config.key";

  public static final int buildNumber = 1;

  @Autowired
  public UpgradeTask1(@ComponentImport final PluginSettingsFactory pluginSettingsFactory) {
    super("0.2.0", pluginSettingsFactory);
  }

  @Override
  public Collection<Message> doUpgrade() throws Exception {
    String configKey = (String) settings.get(KEY_SETTINGS_NAME);

    if (configKey != null && !configKey.isEmpty()) {
      // This means we are upgrading from 0.1.0 to 0.2.0
      // We need it because we did not set the configured version previously
      // So this is an alternative but just for the version 0.1.0
      settings.put(Constants.CONFIGURED_VERSION_KEY, pluginVersion);
    }

    return Collections.emptySet();
  }
}
