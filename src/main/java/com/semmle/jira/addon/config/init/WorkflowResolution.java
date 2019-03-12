package com.semmle.jira.addon.config.init;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;

public class WorkflowResolution {
  @JsonProperty public final String originalId;
  @JsonProperty public final String name;
  @JsonProperty public final String description;

  @JsonCreator
  public WorkflowResolution(
      @JsonProperty("originalId") String originalId,
      @JsonProperty("name") String name,
      @JsonProperty("description") String description) {
    this.originalId = originalId;
    this.name = name;
    this.description = description;
  }
}
