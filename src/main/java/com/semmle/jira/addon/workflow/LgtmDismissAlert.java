package com.semmle.jira.addon.workflow;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.workflow.function.issue.AbstractJiraFunctionProvider;
import com.google.gson.Gson;
import com.opensymphony.module.propertyset.PropertySet;
import com.opensymphony.workflow.InvalidInputException;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;

/**
 * This is the post-function class that gets executed at the end of the transition. Any parameters
 * that were saved in your factory class will be available in the transientVars Map.
 */
public class LgtmDismissAlert extends AbstractJiraFunctionProvider {
  public static final String FIELD_URL = "urlField";
  public static final String FIELD_TRANSITION = "transitionField";

  @SuppressWarnings("rawtypes")
  public void execute(Map transientVars, Map args, PropertySet ps) throws WorkflowException {

    URL url = getUrlParam(args);
    Transition transition = getTransitionParam(args);
    MutableIssue issue = getIssue(transientVars);
    Request message = new Request(transition, issue.getId());

    postMessage(url, message);
  }

  protected void postMessage(URL url, Request message) throws WorkflowException {
    byte[] body = new Gson().toJson(message).getBytes(StandardCharsets.UTF_8);
    try {
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      try {
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Content-Length", Integer.toString(body.length));
        connection.setInstanceFollowRedirects(true);
        connection.setDoOutput(true);
        connection.connect();
        connection.getOutputStream().write(body);
        connection.getResponseCode();
      } finally {
        connection.disconnect();
      }
    } catch (IOException e) {
      throw new WorkflowException("Failed to send update to LGTM", e);
    }
  }

  private URL getUrlParam(Map<?, ?> args) throws InvalidInputException {
    String urlArg = (String) args.get(FIELD_URL);
    URL url;
    if (urlArg == null) {
      throw new InvalidInputException("Missing value for 'url' parameter");
    }
    try {
      url = new URL(urlArg.toString());
    } catch (MalformedURLException e) {
      throw new InvalidInputException("Invalid value for 'url' parameter: " + urlArg);
    }

    return url;
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
