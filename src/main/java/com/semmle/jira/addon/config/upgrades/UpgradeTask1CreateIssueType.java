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
public class UpgradeTask1CreateIssueType implements PluginUpgradeTask {
  private final int BUILD_NUMER = 1;

  @Override
  public Collection<Message> doUpgrade() {
    JiraUtils.createLgtmIssueType();

    return Collections.emptySet();
  }

  @Override
  public int getBuildNumber() {
    return BUILD_NUMER;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task " + BUILD_NUMER + " create 'LGTM alert' issue type.";
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }
}
