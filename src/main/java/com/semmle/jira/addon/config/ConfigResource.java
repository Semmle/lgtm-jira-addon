package com.semmle.jira.addon.config;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.UserUtils;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.atlassian.sal.api.user.UserManager;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.JiraUtils;
import com.semmle.jira.addon.util.StatusNotFoundException;
import com.semmle.jira.addon.util.WorkflowNotFoundException;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import org.ofbiz.core.entity.GenericEntityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/")
@Scanned
public class ConfigResource {
  private static final Logger log = LoggerFactory.getLogger(ConfigResource.class);

  private static final String KEY_SETTINGS_NAME = "com.lgtm.addon.config.key";

  @ComponentImport private final UserManager userManager;
  @ComponentImport private final PluginSettingsFactory pluginSettingsFactory;
  @ComponentImport private final TransactionTemplate transactionTemplate;

  @Inject
  public ConfigResource(
      UserManager userManager,
      PluginSettingsFactory pluginSettingsFactory,
      TransactionTemplate transactionTemplate) {
    this.userManager = userManager;
    this.pluginSettingsFactory = pluginSettingsFactory;
    this.transactionTemplate = transactionTemplate;
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(@Context HttpServletRequest request) {
    String username = userManager.getRemoteUsername(request);
    if (username == null || !userManager.isSystemAdmin(username)) {
      return Response.status(Status.UNAUTHORIZED).build();
    }

    /**
     * Right now we only have one config page, where we don't now what is the key of the current
     * (and only) configuration, therefore, we cannot send it in the request. To solve that we store
     * it in the settings.
     */
    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
    String configKey = (String) settings.get(KEY_SETTINGS_NAME);

    return Response.ok(Config.get(configKey, transactionTemplate, pluginSettingsFactory)).build();
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response put(final Config config, @Context HttpServletRequest request) {
    String username = userManager.getRemoteUsername(request);
    if (username == null || !userManager.isSystemAdmin(username)) {
      return Response.status(Status.UNAUTHORIZED).build();
    }

    if (!UserUtils.userExists(config.getUsername())) {
      return Response.status(Status.BAD_REQUEST).header("Error", "username").build();
    }

    Project project =
        ComponentAccessor.getProjectManager().getProjectByCurrentKey(config.getProjectKey());
    if (project == null) {
      return Response.status(Status.BAD_REQUEST).header("Error", "project").build();
    }

    IssueType lgtmIssueType = JiraUtils.getIssueTypeByName(Constants.ISSUE_TYPE_NAME);
    if (lgtmIssueType == null) {
      return Response.status(Status.BAD_REQUEST).header("Error", "issueType").build();
    }

    JiraUtils.addIssueTypeToProject(project, lgtmIssueType);
    try {
      JiraUtils.addWorkflowToProject(project, Constants.WORKFLOW_NAME, lgtmIssueType);
    } catch (GenericEntityException e) {
      log.error("Error while adding the LGTM workflow to the project", e);
      return Response.status(Status.BAD_REQUEST).header("Error", "workflow").build();
    }

    try {
      config.setReopenedStatusId(
          JiraUtils.getLgtmWorkflowStatus(Constants.WORKFLOW_OPEN_STATUS_NAME).getId());
      config.setClosedStatusId(
          JiraUtils.getLgtmWorkflowStatus(Constants.WORKFLOW_CLOSED_STATUS_NAME).getId());
    } catch (StatusNotFoundException e) {
      return Response.status(Status.BAD_REQUEST).header("Error", "status").build();
    } catch (WorkflowNotFoundException e) {
      return Response.status(Status.BAD_REQUEST).header("Error", "workflow-not-found").build();
    }

    Config.put(config, transactionTemplate, pluginSettingsFactory);
    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
    settings.put(KEY_SETTINGS_NAME, config.getKey());

    return Response.noContent().build();
  }
}
