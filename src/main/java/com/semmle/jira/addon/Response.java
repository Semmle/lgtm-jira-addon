package com.semmle.jira.addon;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {
  @JsonProperty("issue-id")
  public final String issueId;

  @JsonCreator
  public Response(@JsonProperty("issue-id") Long issueId) {
    this.issueId = issueId.toString();
  }
}
