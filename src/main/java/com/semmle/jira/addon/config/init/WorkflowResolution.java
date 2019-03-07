package com.semmle.jira.addon.config.init;

public class WorkflowResolution {
  public final String originalId;
  public final String name;
  public final String description;

  public WorkflowResolution(String originalId, String name, String description) {
    this.originalId = originalId;
    this.name = name;
    this.description = description;
  }
}
