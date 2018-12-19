package com.semmle.jira.addon;

import com.google.gson.annotations.SerializedName;

public class Response {
  @SerializedName("issue-id")
  public final String issueId;

  public Response(Long issueId) {
    this.issueId = issueId.toString();
  }
}
