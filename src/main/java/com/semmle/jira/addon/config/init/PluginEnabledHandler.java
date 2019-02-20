package com.semmle.jira.addon.config.init;

import com.atlassian.event.api.EventListener;
import com.atlassian.event.api.EventPublisher;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.config.IssueTypeManager;
import com.atlassian.jira.workflow.ConfigurableJiraWorkflow;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowUtil;
import com.atlassian.plugin.Plugin;
import com.atlassian.plugin.event.events.PluginEnabledEvent;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.opensymphony.workflow.FactoryException;
import com.opensymphony.workflow.loader.WorkflowDescriptor;
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
  private static final String ISSUE_TYPE_NAME = "LGTM alert";
  private static final String WORKFLOW_NAME = "LGTM";

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
  public void onPluginEnabled(PluginEnabledEvent event) {
    Plugin plugin = event.getPlugin();
    if (LGTM_PLUGIN_KEY.equals(plugin.getKey())) {
      createLgtmIssueType(issueTypeManager);
      createLgtmWorkflow();
    }
  }

  private static void createLgtmIssueType(IssueTypeManager issueTypeManager) {
    try {
      issueTypeManager.createIssueType(ISSUE_TYPE_NAME, "Issue type for managing LGTM alerts", 0l);
    } catch (IllegalStateException e) {
      // Issue Type already created
    }
  }

  private static boolean createLgtmWorkflow() {
    ClassLoader classLoader = PluginEnabledHandler.class.getClassLoader();
    InputStream is = classLoader.getResourceAsStream("xml/LGTM.xml");
    String xml = null;
    try {
      xml = IOUtils.toString(is);
    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    System.out.print(xml);

    WorkflowDescriptor wf = null;
    try {
      wf = WorkflowUtil.convertXMLtoWorkflowDescriptor(xml);
    } catch (FactoryException e) {
      e.printStackTrace();
      return false;
    }
    JiraWorkflow w =
        new ConfigurableJiraWorkflow(WORKFLOW_NAME, wf, ComponentAccessor.getWorkflowManager());
    if (!ComponentAccessor.getWorkflowManager().workflowExists(w.getName())) {
      ComponentAccessor.getWorkflowManager()
          .createWorkflow(ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser(), w);
      return true;
    }
    return false;
  }
}
