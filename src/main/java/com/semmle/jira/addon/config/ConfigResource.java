package com.semmle.jira.addon.config;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.UserUtils;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;
import com.semmle.jira.addon.config.Config.Error;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.JiraUtils;
import com.semmle.jira.addon.util.UsedIssueTypeException;
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
import org.springframework.stereotype.Component;

@Component
@Path("/")
public class ConfigResource {
  private static final Logger log = LoggerFactory.getLogger(ConfigResource.class);

  private static final String KEY_SETTINGS_NAME = "com.lgtm.addon.config.key";

  @ComponentImport private final UserManager userManager;
  @ComponentImport private final PluginSettingsFactory pluginSettingsFactory;

  private final PluginSettings settings;

  @Inject
  public ConfigResource(UserManager userManager, PluginSettingsFactory pluginSettingsFactory) {
    this.userManager = userManager;
    this.pluginSettingsFactory = pluginSettingsFactory;

    settings = pluginSettingsFactory.createGlobalSettings();
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  public Response get(@Context HttpServletRequest request) {
    UserKey userKey = userManager.getRemoteUserKey(request);
    if (userKey == null || !userManager.isSystemAdmin(userKey)) {
      return Response.status(Status.UNAUTHORIZED).build();
    }

    /**
     * Right now we only have one config page, where we don't now what is the key of the current
     * (and only) configuration, therefore, we cannot send it in the request. To solve that we store
     * it in the settings.
     */
    String configKey = (String) settings.get(KEY_SETTINGS_NAME);

    return Response.ok(Config.get(configKey)).build();
  }

  @PUT
  @Consumes(MediaType.APPLICATION_JSON)
  public Response put(Config config, @Context HttpServletRequest request) {
    UserKey userKey = userManager.getRemoteUserKey(request);
    if (userKey == null || !userManager.isSystemAdmin(userKey)) {
      return Response.status(Status.UNAUTHORIZED).build();
    }

    Error configError = config.validate();
    if (configError != null) {
      switch (configError) {
        case MISSING_CONFIG_KEY:
        case MISSING_PROJECT_KEY:
        case MISSING_SECRET:
        case MISSING_USERNAME:
          // Should not happen as the JS checks for it
          return Response.status(Status.BAD_REQUEST).header("Error", "missing-fields").build();
        case PROJECT_NOT_FOUND:
          return Response.status(Status.BAD_REQUEST).header("Error", "project-not-found").build();
        case USER_NOT_FOUND:
          return Response.status(Status.BAD_REQUEST).header("Error", "user-not-found").build();
      }
    }

    Project project = config.getProject();
    IssueType lgtmIssueType = JiraUtils.getIssueTypeByName(Constants.ISSUE_TYPE_NAME);
    if (lgtmIssueType == null) {
      return Response.status(Status.BAD_REQUEST).header("Error", "issueType-not-found").build();
    }
    JiraUtils.addIssueTypeToProject(project, lgtmIssueType);

    try {
      JiraUtils.configureWorkflowForProject(
          project, lgtmIssueType, UserUtils.getUser(config.getUsername()));
    } catch (WorkflowNotFoundException e) {
      return Response.status(Status.BAD_REQUEST).header("Error", "workflow-not-found").build();
    } catch (GenericEntityException e) {
      log.error("Error while adding the LGTM workflow to the project", e);
      return Response.status(Status.BAD_REQUEST).header("Error", "workflow-generic-error").build();
    } catch (UsedIssueTypeException e) {
      return Response.status(Status.BAD_REQUEST).header("Error", "manual-migration-needed").build();
    }

    Config.put(config);
    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
    settings.put(KEY_SETTINGS_NAME, config.getKey());

    String version =
        ComponentAccessor.getPluginAccessor()
            .getPlugin(Constants.PLUGIN_KEY)
            .getPluginInformation()
            .getVersion();
    settings.put(Constants.CONFIGURED_VERSION_KEY, version);

    return Response.noContent().build();
  }
}
