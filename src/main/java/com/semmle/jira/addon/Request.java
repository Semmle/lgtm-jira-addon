package com.semmle.jira.addon;

import com.google.gson.annotations.SerializedName;

public class Request {

  public final Transition transition;

  @SerializedName("issue-id")
  public final Long issueId;

  public final Project project;
  public final Alert alert;

  public Request(Transition transition, Long issueId, Project project, Alert alert) {
    this.transition = transition;
    this.issueId = issueId;
    this.project = project;
    this.alert = alert;
  }

  boolean isValid() {
    if (transition == null) return false;

    switch (transition) {
      case CREATE:
        if (issueId != null) return false;
        if (project == null || !project.isValid()) return false;
        if (alert == null || !alert.isValid()) return false;
        break;

      default:
        if (issueId == null) return false;
        if (project != null) return false;
        if (alert != null) return false;
    }
    return true;
  }

  String getDescription() {
    StringBuilder description = new StringBuilder();

    // Rule name with link to rule help
    description
        .append("*[")
        .append(Util.escape(this.alert.query.name))
        .append("|")
        .append(Util.escape(this.alert.query.url))
        .append("]*\n\n");
    // File path where alert was found
    description.append("In {{").append(Util.escape(this.alert.file)).append("}}:\n");
    // Alert message
    if (this.alert.message != null) {
      description.append("{quote}");
      description.append(Util.escape(this.alert.message.trim()));
      description.append("{quote}\n");
    }
    // View alert on LGTM link
    description.append("[View alert on LGTM|").append(Util.escape(this.alert.url)).append("]");

    return description.toString();
  }

  String getSummary() {
    return String.format("%s (%s)", this.alert.query.name, this.project.name);
  }

  public enum Transition {
    @SerializedName("create")
    CREATE("create"),
    @SerializedName("reopen")
    REOPEN("reopen"),
    @SerializedName("close")
    CLOSE("close");

    public final String value;

    Transition(String value) {
      this.value = value;
    }
  }

  public static class Project {
    public final Long id;

    @SerializedName("url-identifier")
    public final String urlIdentifier;

    public final String name;
    public final String url;

    public Project(Long id, String urlIdentifier, String name, String url) {
      this.id = id;
      this.urlIdentifier = urlIdentifier;
      this.name = name;
      this.url = url;
    }

    boolean isValid() {
      if (id == null) return false;
      if (urlIdentifier == null) return false;
      if (name == null) return false;
      if (url == null) return false;
      return true;
    }
  }

  public static class Alert {
    public final String file;
    public final String message;
    public final String url;
    public final Query query;

    public Alert(String file, String message, String url, Query query) {
      this.file = file;
      this.message = message;
      this.url = url;
      this.query = query;
    }

    boolean isValid() {
      if (file == null) return false;
      if (message == null) return false;
      if (url == null) return false;
      if (query == null || !query.isValid()) return false;
      return true;
    }

    public static class Query {
      public final String name;
      public final String url;

      public Query(String name, String url) {
        this.name = name;
        this.url = url;
      }

      boolean isValid() {
        if (name == null) return false;
        if (url == null) return false;
        return true;
      }
    }
  }
}
