package com.semmle.jira.addon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.junit.rules.MockitoContainer;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Rule;

public class TestLgtmServletBase {

  @Rule public MockitoContainer mockitoContainer = new MockitoContainer(this);

  @AvailableInContainer
  PluginSettingsFactory pluginSettingsFactory = mock(PluginSettingsFactory.class);;
  protected MockPluginSettings pluginSettings;
  TransactionTemplate transactionTemplate;
  LgtmServlet servlet;
  List<String> log = new ArrayList<>();

  @Before
  public void setupServlet() {
    pluginSettings = new MockPluginSettings();
    when(pluginSettingsFactory.createGlobalSettings()).thenReturn(pluginSettings);
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

  private static class MockServletOutputStream extends ServletOutputStream {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    @Override
    public void write(int b) {
      out.write(b);
    }

    @Override
    public String toString() {
      try {
        return out.toString("UTF-8");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }
  }

  public HttpServletResponse mockResponse() throws IOException {
    HttpServletResponse resp = mock(HttpServletResponse.class);
    MockServletOutputStream out = new MockServletOutputStream();

    when(resp.getOutputStream()).thenReturn(out);

    return resp;
  }
}
