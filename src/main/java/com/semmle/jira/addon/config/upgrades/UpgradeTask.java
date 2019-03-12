package com.semmle.jira.addon.config.upgrades;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;

public abstract class UpgradeTask implements PluginUpgradeTask {
  protected final String CONFIGURED_VERSION_KEY = "com.lgtm.addon.version";

  protected final PluginSettings settings;

  protected final int buildNumber;

  public UpgradeTask(int buildNumber, PluginSettingsFactory pluginSettingsFactory) {
    this.buildNumber = buildNumber;
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
    Object configuredVersionObject = settings.get(CONFIGURED_VERSION_KEY);
    if (configuredVersionObject == null) return true;
    int configuredVersion = (int) configuredVersionObject;

    return configuredVersion > buildNumber;
  }

  /**
   * Sets the current configured version in the plugin settings. This must be called when the
   * upgrade succeeds.
   */
  protected void putConfiguredVersion() {
    settings.put(CONFIGURED_VERSION_KEY, buildNumber);
  }
}
