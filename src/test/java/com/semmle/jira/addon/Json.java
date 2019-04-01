package com.semmle.jira.addon;

import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.util.Util;
import com.semmle.jira.addon.util.workflow.WorkflowResolution;
import com.semmle.jira.addon.util.workflow.WorkflowStatus;
import java.io.IOException;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.TextNode;
import org.junit.Assert;
import org.junit.Test;

public class Json {

  @Test
  public void test() throws IOException {

    // Simple transition request
    String text = "{\"transition\":\"close\",\"issue-id\":1}";
    testJsonRoundTrip(text, Request.class, true);

    // Detailed create request
    text =
        "{\"transition\":\"create\",\"issue-id\":1,\"project\":{\"id\":2,"
            + "\"url-identifier\":\"g/commons/io\",\"name\":\"CommonsIO\","
            + "\"url\":\"URL\"},\"alert\":{\"file\":\"file\",\"message\":"
            + "\"name\",\"url\":\"alert-url\",\"query\":{\"name\":\"name\""
            + ",\"url\":\"query-url\"}}}";

    testJsonRoundTrip(text, Request.class, true);

    // Response JSON
    text = "{\"issue-id\":\"1\"}";
    testJsonRoundTrip(text, Response.class, true);

    // JSON error message
    text = "{\"code\":500,\"error\":\"hello world\"}";
    testJsonRoundTrip(text, JsonError.class, true);

    // WorkflowResolution
    text = "{\"originalId\":\"id\",\"name\":\"name\",\"description\":\"desc\"}";
    testJsonRoundTrip(text, WorkflowResolution.class, false);

    // WorkflowStatus
    text =
        "{\"originalId\":\"id\",\"name\":\"name\",\"description\":\"desc\",\"statusCategoryId\":\"category\"}";
    testJsonRoundTrip(text, WorkflowStatus.class, false);

    text =
        "{\"key\":\"config_key\",\"lgtmSecret\":\"12345678\",\"username\":\"admin\",\"projectKey\":\"MKY\",\"externalHookUrl\":\"www.lgtm.com\",\"trackerKey\":\"trackerKey\"}";
    testJsonRoundTrip(text, Config.class, false);
  }

  private static <T> void testJsonRoundTrip(String input, Class<T> cls, boolean unknownFields)
      throws IOException {
    T obj = Util.JSON.readValue(input, cls);
    Assert.assertEquals(input, Util.JSON.writeValueAsString(obj));
    if (unknownFields) {
      JsonNode json = Util.JSON.readValue(input, JsonNode.class);
      insertUnknownField(json);
      String inputWithCrap = Util.JSON.writeValueAsString(json);
      obj = Util.JSON.readValue(inputWithCrap, cls);
      Assert.assertEquals(input, Util.JSON.writeValueAsString(obj));
    }
  }

  private static void insertUnknownField(JsonNode json) {
    if (json instanceof ObjectNode) {
      ((ObjectNode) json).put("unknownField", new TextNode("some data"));
      json.forEach(Json::insertUnknownField);
    }
  }
}
