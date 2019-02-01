package com.semmle.jira.addon.config;

public class InvalidConfigurationException extends Exception {
  private static final long serialVersionUID = -5124641799683500864L;

  public InvalidConfigurationException(String message) {
    super(message);
  }
}
