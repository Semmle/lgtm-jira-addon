package com.semmle.jira.addon.config.upgrades;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.workflow.JiraWorkflow;
import com.atlassian.jira.workflow.WorkflowManager;
import com.atlassian.plugin.spring.scanner.annotation.export.ExportAsService;
import com.atlassian.sal.api.message.Message;
import com.atlassian.sal.api.upgrade.PluginUpgradeTask;
import com.opensymphony.workflow.loader.ActionDescriptor;
import com.opensymphony.workflow.loader.DescriptorFactory;
import com.opensymphony.workflow.loader.FunctionDescriptor;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.workflow.LgtmSetLabelsFunction;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@ExportAsService(PluginUpgradeTask.class)
@Component
public class UpgradeTask5AddSetLabelsFunction implements PluginUpgradeTask {
  private final int BUILD_NUMER = 5;

  @Override
  public Collection<Message> doUpgrade() {

    FunctionDescriptor setLabelsFunction =
        DescriptorFactory.getFactory().createFunctionDescriptor();
    setLabelsFunction.setType("class");

    Map<String, String> args = setLabelsFunction.getArgs();
    args.put("full.module.key", "com.semmle.lgtm-jira-addonlgtm-set-labels");
    args.put("class.name", LgtmSetLabelsFunction.class.getName());
    args.put("project.label", "true");

    WorkflowManager workflowManager = ComponentAccessor.getWorkflowManager();
    JiraWorkflow workflow = workflowManager.getWorkflow(Constants.WORKFLOW_NAME);

    List<ActionDescriptor> initialActions = workflow.getDescriptor().getInitialActions();
    for (ActionDescriptor action : initialActions) {
      List<FunctionDescriptor> functions = action.getUnconditionalResult().getPostFunctions();
      boolean alreadyExists = false;
      Iterator<?> iter = functions.iterator();
      while (iter.hasNext()) {
        Object obj = iter.next();
        if (obj instanceof FunctionDescriptor) {
          FunctionDescriptor fun = (FunctionDescriptor) obj;
          if (LgtmSetLabelsFunction.class.getName().equals(fun.getArgs().get("class.name"))) {
            alreadyExists = true;
            break;
          }
        }
      }
      if (!alreadyExists) {
        functions.add(0, setLabelsFunction);
        workflowManager.saveWorkflowWithoutAudit(workflow);
      }
    }

    return Collections.emptySet();
  }

  @Override
  public int getBuildNumber() {
    return BUILD_NUMER;
  }

  @Override
  public String getShortDescription() {
    return "Upgrade Task " + BUILD_NUMER + " add Set Labels post function to workflow";
  }

  @Override
  public String getPluginKey() {
    return Constants.PLUGIN_KEY;
  }
}
