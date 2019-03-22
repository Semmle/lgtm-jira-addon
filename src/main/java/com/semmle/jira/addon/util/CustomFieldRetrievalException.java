package com.semmle.jira.addon.util;

public class CustomFieldRetrievalException extends Exception {

  private static final long serialVersionUID = 1L;

  public CustomFieldRetrievalException(String message) {
    super(message);
  }

  public CustomFieldRetrievalException(String message, Exception e) {
    super(message, e);
  }
}
