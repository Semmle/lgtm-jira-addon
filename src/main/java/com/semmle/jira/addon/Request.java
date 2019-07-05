package com.semmle.jira.addon;

import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.annotate.JsonValue;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Request {

  @JsonProperty public final Transition transition;

  @JsonProperty("issue-id")
  public final Long issueId;

  @JsonProperty public final Project project;
  @JsonProperty public final Alert alert;

  public final JsonNode jsonRequest;

  public Request(JsonNode jsonRequest) throws IOException {
    Request request = Util.JSON.readValue(jsonRequest, Request.class);
    this.transition = request.transition;
    this.issueId = request.issueId;
    this.project = request.project;
    this.alert = request.alert;

    this.jsonRequest = jsonRequest;
  }

  public Request(Transition transition, Long issueId) {
    this(transition, issueId, null, null);
  }

  @JsonCreator
  public Request(
      @JsonProperty("transition") Transition transition,
      @JsonProperty("issue-id") Long issueId,
      @JsonProperty("project") Project project,
      @JsonProperty("alert") Alert alert) {
    this.transition = transition;
    this.issueId = issueId;
    this.project = project;
    this.alert = alert;

    this.jsonRequest = null;
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

  public static enum Transition {
    CREATE("create"),
    REOPEN("reopen"),
    CLOSE("close"),
    SUPPRESS("suppress"),
    UNSUPPRESS("unsuppress");

    public final String value;

    Transition(String value) {
      this.value = value;
    }

    @JsonCreator
    public static Transition fromString(String value) {
      for (Transition transition : Transition.values()) {
        if (transition.value.equals(value)) return transition;
      }
      throw new IllegalArgumentException("Invalid transition:" + value);
    }

    @JsonValue
    public String toString() {
      return value;
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Project {
    @JsonProperty public final Long id;

    @JsonProperty("url-identifier")
    public final String urlIdentifier;

    @JsonProperty public final String name;
    @JsonProperty public final String url;

    @JsonCreator
    public Project(
        @JsonProperty("id") Long id,
        @JsonProperty("url-identifier") String urlIdentifier,
        @JsonProperty("name") String name,
        @JsonProperty("url") String url) {
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

  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Alert {
    @JsonProperty public final String file;
    @JsonProperty public final String message;
    @JsonProperty public final String url;
    @JsonProperty public final Query query;

    @JsonCreator
    public Alert(
        @JsonProperty("file") String file,
        @JsonProperty("message") String message,
        @JsonProperty("url") String url,
        @JsonProperty("query") Query query) {
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Query {
      @JsonProperty public final String name;
      @JsonProperty public final Properties properties;
      @JsonProperty public final String url;

      public static class Properties {
        @JsonProperty public final String severity;

        @JsonCreator
        public Properties(@JsonProperty("severity") String severity) {
          this.severity = severity;
        }
      }

      @JsonCreator
      public Query(
          @JsonProperty("name") String name,
          @JsonProperty("properties") Properties properties,
          @JsonProperty("url") String url) {
        this.name = name;
        this.properties = properties;
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
