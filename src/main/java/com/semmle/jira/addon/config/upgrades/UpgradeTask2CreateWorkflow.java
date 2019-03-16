package com.semmle.jira.addon.config.upgrades;

import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.semmle.jira.addon.Util;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.workflow.WorkflowResolution;
import com.semmle.jira.addon.util.workflow.WorkflowStatus;
import com.semmle.jira.addon.util.workflow.WorkflowUtils;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask2CreateWorkflow implements PluginUpgradeTask {
  private final int BUILD_NUMER = 2;

  @Override
  public Collection<Message> doUpgrade() throws Exception {
    ClassLoader classLoader = UpgradeTask2CreateWorkflow.class.getClassLoader();
    String workflowXml;
    try (InputStream is = classLoader.getResourceAsStream("workflow/workflow.xml")) {
      workflowXml = IOUtils.toString(is, "UTF-8");
    }

    WorkflowStatus[] statuses;
    try (InputStream is = classLoader.getResourceAsStream("workflow/statuses.json")) {
      statuses = Util.JSON.readValue(is, WorkflowStatus[].class);
    }

    WorkflowResolution[] resolutions;
    try (InputStream is = classLoader.getResourceAsStream("workflow/resolutions.json")) {
      resolutions = Util.JSON.readValue(is, WorkflowResolution[].class);
    }

    WorkflowUtils.createWorkflow(Constants.WORKFLOW_NAME, workflowXml, statuses, resolutions);

    String layoutJson;
    try (InputStream is = classLoader.getResourceAsStream("workflow/layout.v2.json")) {
      layoutJson = IOUtils.toString(is, "UTF-8");
    }
    WorkflowUtils.addLayoutToWorkflow(Constants.WORKFLOW_NAME, layoutJson);

    return Collections.emptySet();
  }

  @Override
  public int getBuildNumber() {
    return BUILD_NUMER;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task " + BUILD_NUMER + " create LGTM workflow.";
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }
}
