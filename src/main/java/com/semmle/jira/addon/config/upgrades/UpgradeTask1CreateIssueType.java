package com.semmle.jira.addon.config.upgrades;

import java.util.Collection;
import java.util.Collections;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.JiraUtils;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask1CreateIssueType implements PluginUpgradeTask {
  public final int BUILD_NUMER = 1;

  protected final PluginSettings settings;

  @Autowired
  public UpgradeTask1CreateIssueType(
      @ComponentImport final PluginSettingsFactory pluginSettingsFactory) {
    this.settings = pluginSettingsFactory.createGlobalSettings();
  }

  @Override
  public Collection<Message> doUpgrade() throws Exception {
	  JiraUtils.createLgtmIssueType();

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
