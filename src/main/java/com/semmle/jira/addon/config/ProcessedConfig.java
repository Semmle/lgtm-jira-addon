package com.semmle.jira.addon.config;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessedConfig {
  private ApplicationUser user;
  private Project project;
  private Priority priorityLevel;

  public ProcessedConfig(Config config) throws InvalidConfigurationException {

    List<String> configErrors = new ArrayList<>();

    if (config.getUsername() != null) {
      user = ComponentAccessor.getUserManager().getUserByName(config.getUsername());
      if (user == null)
        configErrors.add(String.format("user '%s' not found", config.getUsername()));
    } else {
      configErrors.add("username was null");
    }

    if (config.getProjectKey() != null) {
      project =
          ComponentAccessor.getProjectManager().getProjectByCurrentKey(config.getProjectKey());
      if (project == null)
        configErrors.add(String.format("project key '%s' not found", config.getProjectKey()));
    } else {
      configErrors.add("projectKey was null");
    }

    priorityLevel = null;
    if (config.getPriorityLevelId() != null && !config.getPriorityLevelId().isEmpty()) {
      priorityLevel =
          ComponentAccessor.getConstantsManager().getPriorityObject(config.getPriorityLevelId());
      if (priorityLevel == null)
        configErrors.add(
            String.format("priority level id '%s' not found", config.getPriorityLevelId()));
    }

    if (!configErrors.isEmpty()) {
      String message =
          configErrors.stream().collect(Collectors.joining(", ", "Invalid configuration – ", "."));
      throw new InvalidConfigurationException(message);
    }
  }

  public ApplicationUser getUser() {
    return user;
  }

  public Project getProject() {
    return project;
  }

  public Priority getPriorityLevel() {
    return priorityLevel;
  }
}
