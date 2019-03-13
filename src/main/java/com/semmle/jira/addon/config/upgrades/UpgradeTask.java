package com.semmle.jira.addon.config.upgrades;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;

public abstract class UpgradeTask implements PluginUpgradeTask {

  public static final int buildNumber = 0;

  protected final String pluginVersion;
  protected final PluginSettings settings;

  public UpgradeTask(String pluginVersion, PluginSettingsFactory pluginSettingsFactory) {
    this.pluginVersion = pluginVersion;
    this.settings = pluginSettingsFactory.createGlobalSettings();
  }

  @Override
  public int getBuildNumber() {
    return buildNumber;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task " + buildNumber;
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }

  protected boolean isFreshInstall() {
    String configuredVersionObject = (String) settings.get(Constants.CONFIGURED_VERSION_KEY);
    return configuredVersionObject == null || configuredVersionObject.isEmpty();
  }

  /**
   * Sets the current configured version in the plugin settings. This must be called when the
   * upgrade succeeds.
   */
  protected void putConfiguredVersion() {
    settings.put(Constants.CONFIGURED_VERSION_KEY, pluginVersion);
  }
}
