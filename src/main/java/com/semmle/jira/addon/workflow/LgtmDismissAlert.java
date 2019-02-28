package com.semmle.jira.addon.workflow;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import com.google.gson.Gson;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.JsonError;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.Util;
import com.semmle.jira.addon.config.Config;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;

/**
 * This is the post-function class that gets executed at the end of the transition. Any parameters
 * that were saved in your factory class will be available in the transientVars Map.
 */
public class LgtmDismissAlert extends AbstractJiraFunctionProvider {
  public static final String FIELD_TRANSITION = "transitionField";

  @ComponentImport private final PluginSettingsFactory pluginSettingsFactory;
  @ComponentImport private final TransactionTemplate transactionTemplate;

  @Inject
  LgtmDismissAlert(
      PluginSettingsFactory pluginSettingsFactory, TransactionTemplate transactionTemplate) {
    this.pluginSettingsFactory = pluginSettingsFactory;
    this.transactionTemplate = transactionTemplate;
  }

  @SuppressWarnings("rawtypes")
  public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {

    String configKey = "webhook"; // TODO get config key from custom field
    Config config = Config.get(configKey, transactionTemplate, pluginSettingsFactory);
    URI url = config.getExternalHookUrl();
    // There is no external hook URL configured
    if (url == null) return;

    Transition transition = getTransitionParam(args);
    MutableIssue issue = getIssue(transientVars);
    Request message = new Request(transition, issue.getId());
    WorkflowException exception = null;
    for (int retries = 0; retries < 3; retries++) {
      try {
        postMessage(config.getLgtmSecret(), url, message);
        break;
      } catch (WorkflowException e) {
        if (exception == null) exception = e;
      }
    }
    if (exception != null) throw exception;
  }

  protected void postMessage(String secret, URI url, Request message) throws WorkflowException {
    Gson gson = new Gson();
    byte[] body = gson.toJson(message).getBytes(StandardCharsets.UTF_8);
    try {
      HttpURLConnection connection = (HttpURLConnection) url.toURL().openConnection();
      try {
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
        String signature = Util.calculateHmac(secret, body);
        connection.setRequestProperty("X-LGTM-Signature", signature);
        connection.setInstanceFollowRedirects(true);
        connection.setDoInput(true);
        connection.setDoOutput(true);
        connection.connect();
        connection.getOutputStream().write(body);
        if (connection.getResponseCode() != HttpServletResponse.SC_OK) {
          String error = null;
          if ("application/json".equals(connection.getContentType())) {
            InputStream input = connection.getErrorStream();
            if (input == null) {
              error = connection.getResponseMessage();
            } else {
              try (Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                JsonError jsonError = gson.fromJson(reader, JsonError.class);
                if (jsonError.error != null) {
                  error = jsonError.error;
                }
              } finally {
                input.close();
              }
            }
          } else {
            error = connection.getResponseMessage();
          }
          if (error == null || error.isEmpty()) {
            error = "An error occurred";
          }
          throw new WorkflowException("Failed to send update to LGTM: " + error);
        }
      } finally {
        connection.disconnect();
      }
    } catch (IOException e) {
      throw new WorkflowException("Failed to send update to LGTM", e);
    }
  }

  private Transition getTransitionParam(Map<?, ?> args) throws InvalidInputException {
    String transitionArg = (String) args.get(FIELD_TRANSITION);
    if (transitionArg == null)
      throw new InvalidInputException("Missing value for 'transition' parameter");
    Transition transition = null;
    for (Transition t : Arrays.asList(Transition.SUPPRESS, Transition.UNSUPPRESS)) {
      if (t.value.equals(transitionArg)) {
        transition = t;
        break;
      }
    }
    if (transition == null)
      throw new InvalidInputException("Invalid value for 'transition' parameter: " + transitionArg);
    return transition;
  }
}
