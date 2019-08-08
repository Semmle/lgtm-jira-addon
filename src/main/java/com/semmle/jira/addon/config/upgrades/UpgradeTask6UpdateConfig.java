package com.semmle.jira.addon.config.upgrades;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.util.Constants;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask6UpdateConfig implements PluginUpgradeTask {
  private final int BUILD_NUMER = 6;

  @Override
  public Collection<Message> doUpgrade() {

    PluginSettingsFactory pluginSettingsFactory =
        ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

    String configKey = (String) settings.get("com.lgtm.addon.config.key");
    // If there are no configuration settings yet, skip the upgrade
    if (configKey == null) {
      return Collections.emptySet();
    }

    // If there already is a correct configuration object, skip the upgrade
    if (settings.get("com.lgtm.addon.config." + configKey) != null) {
      return Collections.emptySet();
    }

    // Create a new configuration object if any of the old properties exist
    String lgtmSecret = (String) settings.get("com.lgtm.addon.config." + configKey + ".lgtmSecret");
    String username = (String) settings.get("com.lgtm.addon.config." + configKey + ".username");
    String projectKey = (String) settings.get("com.lgtm.addon.config." + configKey + ".projectKey");
    String externalHookUrl =
        (String) settings.get("com.lgtm.addon.config." + configKey + ".externalHookUrl");
    if (lgtmSecret != null || username != null || projectKey != null || externalHookUrl != null) {
      Properties properties = new Properties();
      properties.setProperty("key", configKey);
      properties.setProperty("lgtmSecret", lgtmSecret);
      properties.setProperty("username", username);
      properties.setProperty("projectKey", projectKey);
      properties.setProperty("externalHookUrl", externalHookUrl);
      Config.put(new Config(properties));
    }
    return Collections.emptySet();
  }

  @Override
  public int getBuildNumber() {
    return BUILD_NUMER;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task " + BUILD_NUMER + " update configuration";
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }
}
