package com.semmle.jira.addon;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class JsonError {
  @JsonProperty public int code;
  @JsonProperty public String error;

  @JsonCreator
  public JsonError(@JsonProperty("code") int code, @JsonProperty("error") String error) {
    this.code = code;
    this.error = error;
  }
}
