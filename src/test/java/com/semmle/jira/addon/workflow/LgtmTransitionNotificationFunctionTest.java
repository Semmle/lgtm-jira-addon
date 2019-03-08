package com.semmle.jira.addon.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.opensymphony.workflow.InvalidInputException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.config.Config;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;

public class LgtmTransitionNotificationFunctionTest {
  private LgtmTransitionNotificationFunction function;
  private MutableIssue issue;
  private Request requestBody;
  private URI requestURL;
  private String secret;

  private static class MockPluginSettings implements PluginSettings {

    private final Map<String, Object> settings = new LinkedHashMap<>();

    @Override
    public Object get(String key) {
      return settings.get(key);
    }

    @Override
    public Object put(String key, Object value) {
      return settings.put(key, value);
    }

    @Override
    public Object remove(String key) {
      return settings.remove(key);
    }
  }

  @Before
  public void setup() {
    issue = mock(MutableIssue.class);
    when(issue.getId()).thenReturn(10L);
    PluginSettingsFactory settingsFactory = mock(PluginSettingsFactory.class);
    PluginSettings settings = new MockPluginSettings();
    when(settingsFactory.createGlobalSettings()).thenReturn(settings);
    TransactionTemplate transaction =
        new TransactionTemplate() {

          @Override
          public <T> T execute(TransactionCallback<T> action) {
            return action.doInTransaction();
          }
        };
    Config config = new Config();
    config.setKey("webhook");
    config.setLgtmSecret("secret");
    config.setExternalHookUrl(URI.create("https://localhost:8080"));
    Config.put(config, transaction, settingsFactory);

    function =
        new LgtmTransitionNotificationFunction(settingsFactory, transaction) {
          @Override
          protected MutableIssue getIssue(@SuppressWarnings("rawtypes") Map transientVars) {
            return issue;
          }

          @Override
          protected void postMessage(String secret, URI url, Request request) {
            LgtmTransitionNotificationFunctionTest.this.secret = secret;
            LgtmTransitionNotificationFunctionTest.this.requestURL = url;
            LgtmTransitionNotificationFunctionTest.this.requestBody = request;
          }
        };
  }

  @Test(expected = InvalidInputException.class)
  public void testNullParameters() throws Exception {
    function.execute(Collections.emptyMap(), Collections.emptyMap(), null);
  }

  @Test
  public void testValidParameters() throws Exception {
    Map<String, String> args = new LinkedHashMap<>();
    args.put(LgtmTransitionNotificationFunction.FIELD_TRANSITION, Transition.SUPPRESS.name());
    function.execute(Collections.emptyMap(), args, null);
    Assert.assertEquals("secret", secret);
    Assert.assertEquals("https://localhost:8080", requestURL.toString());
    Assert.assertEquals(Transition.SUPPRESS, requestBody.transition);
    Assert.assertEquals(Long.valueOf(10), requestBody.issueId);
  }
}
