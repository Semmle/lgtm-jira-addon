package com.semmle.jira.addon.workflow;

import java.util.Objects;

public enum ComparisonOperator {
  EQUALS("is"),
  NOT_EQUALS("is not");

  private final String value;

  private ComparisonOperator(String value) {
    this.value = value;
  }

  @Override
  public String toString() {
    return value;
  }

  ComparisonOperator negate() {
    switch (this) {
      case EQUALS:
        return NOT_EQUALS;
      case NOT_EQUALS:
        return EQUALS;
      default:
        throw new IllegalStateException("Missing case for negate: " + this);
    }
  }

  boolean test(String x, String y) {
    switch (this) {
      case EQUALS:
        return Objects.equals(x, y);
      case NOT_EQUALS:
        return !Objects.equals(x, y);
      default:
        throw new IllegalStateException("Missing case for test: " + this);
    }
  }
}
