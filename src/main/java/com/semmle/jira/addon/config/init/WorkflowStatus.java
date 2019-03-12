package com.semmle.jira.addon.config.init;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class WorkflowStatus {
  @JsonProperty public final String originalId;
  @JsonProperty public final String name;
  @JsonProperty public final String description;
  @JsonProperty public final String statusCategoryId;

  @JsonCreator
  public WorkflowStatus(
      @JsonProperty("originalId") String originalId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description,
      @JsonProperty("statusCategoryId") String statusCategoryId) {
    this.originalId = originalId;
    this.name = name;
    this.description = description;
    this.statusCategoryId = statusCategoryId;
  }
}
