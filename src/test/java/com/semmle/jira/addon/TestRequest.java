package com.semmle.jira.addon;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class TestRequest {
  Request request;

  @Before
  public void createRequest() {
    request = TestCreateIssue.createRequest("test", "Query", "test.cpp", "Security Error");
  }

  @Test
  public void testGetDescription() {
    String expected =
        "*[Query|www.test.com/Query]*\n"
            + "\n"
            + "In {{test.cpp}}:\n"
            + "{quote}Security Error{quote}\n"
            + "[View alert on LGTM|www.test.com/alert]";

    assertEquals(expected, request.getDescription());
  }

  @Test
  public void testGetSummary() {
    String expected = "Query (test)";

    assertEquals(expected, request.getSummary());
  }
}
