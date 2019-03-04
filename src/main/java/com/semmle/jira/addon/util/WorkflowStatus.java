package com.semmle.jira.addon.util;

public class WorkflowStatus {
  public final String originalId;
  public final String name;
  public final String description;
  public final String statusCategoryId;

  public WorkflowStatus(
      String originalId, String name, String description, String statusCategoryId) {
    this.originalId = originalId;
    this.name = name;
    this.description = description;
    this.statusCategoryId = statusCategoryId;
  }
}
