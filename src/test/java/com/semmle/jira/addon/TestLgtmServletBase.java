package com.semmle.jira.addon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.junit.rules.MockitoContainer;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;

public class TestLgtmServletBase {

  @Rule public MockitoContainer mockitoContainer = new MockitoContainer(this);

  PluginSettingsFactory pluginSettingsFactory;
  TransactionTemplate transactionTemplate;
  LgtmServlet servlet;
  List<String> log = new ArrayList<>();

  @Before
  public void setupServlet() {
    pluginSettingsFactory = mock(PluginSettingsFactory.class);
    transactionTemplate = mock(TransactionTemplate.class);
    servlet =
        new LgtmServlet(pluginSettingsFactory, transactionTemplate) {
          private static final long serialVersionUID = 1L;

          @Override
          public void log(String msg) {
            log.add(msg);
          }
        };
  }

  public HttpServletResponse mockResponse() throws IOException {
    HttpServletResponse resp = mock(HttpServletResponse.class);
    PrintWriter respWriter = mock(PrintWriter.class);
    when(resp.getWriter()).thenReturn(respWriter);

    return resp;
  }
}
