package com.semmle.jira.addon.config.init;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.opensymphony.workflow.FactoryException;
import com.semmle.jira.addon.util.Constants;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

@ExportAsService({PluginEnabledHandler.class})
@Named("pluginEnabledHandler")
@Scanned
public class PluginEnabledHandler implements InitializingBean, DisposableBean {

  private static final String LGTM_PLUGIN_KEY = "com.semmle.lgtm-jira-addon";

  @ComponentImport private final EventPublisher eventPublisher;
  @ComponentImport private final IssueTypeManager issueTypeManager;

  @Inject
  public PluginEnabledHandler(EventPublisher eventPublisher, IssueTypeManager issueTypeManager) {
    this.eventPublisher = eventPublisher;
    this.issueTypeManager = issueTypeManager;
  }

  @Override
  public void afterPropertiesSet() {
    eventPublisher.register(this);
  }

  @Override
  public void destroy() {
    eventPublisher.unregister(this);
  }

  @EventListener
  public void onPluginEnabled(PluginEnabledEvent event) throws FactoryException, IOException {
    Plugin plugin = event.getPlugin();
    if (LGTM_PLUGIN_KEY.equals(plugin.getKey())) {
      createLgtmWorkflow();
    }
  }

  private static void createLgtmWorkflow() throws IOException, FactoryException {
    ClassLoader classLoader = PluginEnabledHandler.class.getClassLoader();
    String workflowXml;
    try (InputStream is = classLoader.getResourceAsStream("workflow/workflow.xml")) {
      workflowXml = IOUtils.toString(is, "UTF-8");
    }

    String statusesJson;
    try (InputStream is = classLoader.getResourceAsStream("workflow/statuses.json")) {
      statusesJson = IOUtils.toString(is, "UTF-8");
    }

    String resolutionsJson;
    try (InputStream is = classLoader.getResourceAsStream("workflow/resolutions.json")) {
      resolutionsJson = IOUtils.toString(is, "UTF-8");
    }

    WorkflowUtils.createWorkflow(
        Constants.WORKFLOW_NAME, workflowXml, statusesJson, resolutionsJson);

    String layoutJson;
    try (InputStream is = classLoader.getResourceAsStream("workflow/layout.v2.json")) {
      layoutJson = IOUtils.toString(is, "UTF-8");
    }
    WorkflowUtils.addLayoutToWorkflow(Constants.WORKFLOW_NAME, layoutJson);
  }
}
