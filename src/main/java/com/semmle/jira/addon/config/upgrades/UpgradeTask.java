package com.semmle.jira.addon.config.upgrades;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;

public abstract class UpgradeTask implements PluginUpgradeTask {
  protected static final String CONFIGURED_VERSION_KEY = "com.lgtm.addon.version";

  protected final PluginSettings settings;

  public static final int buildNumber = 0;
  public static final int highestBuildNumber = 1;

  public UpgradeTask(PluginSettingsFactory pluginSettingsFactory) {
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
    if (isFreshInstall()) {
      settings.put(CONFIGURED_VERSION_KEY, highestBuildNumber);
    } else {
      settings.put(CONFIGURED_VERSION_KEY, buildNumber);
    }
  }
}
