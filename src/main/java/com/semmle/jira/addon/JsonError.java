package com.semmle.jira.addon;

public class JsonError {
  public int code;
  public String error;

  public JsonError(int code, String error) {
    this.code = code;
    this.error = error;
  }
}
