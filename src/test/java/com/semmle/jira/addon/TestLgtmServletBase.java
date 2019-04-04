package com.semmle.jira.addon;

import com.atlassian.jira.junit.rules.MockitoContainer;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;

public class TestLgtmServletBase {

  @Rule public MockitoContainer mockitoContainer = new MockitoContainer(this);

  LgtmServlet servlet;
  List<String> log = new ArrayList<>();

  @Before
  public void setupServlet() {
    servlet =
        new LgtmServlet() {
          private static final long serialVersionUID = 1L;

          @Override
          public void log(String msg) {
            log.add(msg);
          }
        };
  }
}
