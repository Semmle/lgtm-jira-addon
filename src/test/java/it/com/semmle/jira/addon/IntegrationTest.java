package it.com.semmle.jira.addon;

import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.atlassian.jira.testkit.client.Backdoor;
import com.atlassian.jira.testkit.client.restclient.Issue;
import com.atlassian.jira.testkit.client.restclient.SearchRequest;
import com.atlassian.jira.testkit.client.util.TestKitLocalEnvironmentData;
import com.atlassian.jira.testkit.client.util.TimeBombLicence;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.google.common.collect.Iterables;
import com.google.gson.GsonBuilder;
import com.semmle.jira.addon.config.Config;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class IntegrationTest {

  Backdoor testKit;
  String baseUrl;
  HttpClient httpClient;
  Config config;

  public IntegrationTest() throws JSONException, ClientProtocolException, IOException {

    // Use TestKit to initialise a test instance of Jira
    testKit = new Backdoor(new TestKitLocalEnvironmentData());
    testKit.restoreBlankInstance(TimeBombLicence.LICENCE_FOR_TESTING);

    baseUrl = testKit.generalConfiguration().getEnvironmentData().getBaseUrl().toString();
    httpClient = HttpClientBuilder.create().build();

    // manually create `LGTM alert` due to issues with plugin lifecycle
    createIssueType();

    config = configurePlugin();
  }

  @Test
  public void testCreationAndTransitionOfIssues() throws IOException, JSONException {

    String createString =
        "{\n"
            + "    \"transition\": \"create\",\n"
            + "    \"project\": {\n"
            + "        \"id\": 1000001,\n"
            + "        \"url-identifier\": \"Git/example_user/example_repo\",\n"
            + "        \"name\": \"example_user/example_repo\",\n"
            + "        \"url\": \"http://lgtm.example.com/projects/Git/example_user/example_repo\"\n"
            + "    },\n"
            + "    \"alert\": {\n"
            + "        \"file\": \"/example.py\",\n"
            + "        \"message\": \"Description of one issue.\\nDescription of another issue.\\n\",\n"
            + "        \"url\": \"http://lgtm.example.com/issues/10000/language/8cdXzW+PyA3qiHBbWFomoMGtiIE=\",\n"
            + "        \"query\": {\n"
            + "            \"name\": \"Example rule\",\n"
            + "            \"url\": \"http://lgtm.example.com/rules/10000\"\n"
            + "        }\n"
            + "    }\n"
            + "}";
    String createSignature = "a520128c3068f74c6d0f54d266dd0b2fe6634c33";

    // Create three issues, subsequent tests assume these have known IDs
    Issue issueA = postWebhookJson(createString, createSignature);
    assertEquals("IssueA id is wrong", "10000", issueA.id);
    Issue issueB = postWebhookJson(createString, createSignature);
    assertEquals("IssueB id is wrong", "10001", issueB.id);
    Issue issueC = postWebhookJson(createString, createSignature);
    assertEquals("IssueC id is wrong", "10002", issueC.id);

    // Close two issues
    postWebhookJson(
        "{\"issue-id\": \"10001\", \"transition\": \"close\" }",
        "486fae456903f273033db2f8fc8c26a019f11f0e");
    postWebhookJson(
        "{\"issue-id\": \"10002\", \"transition\": \"close\" }",
        "6bedc9b786705787c1522118f5723c94122d5200");

    // Reopen one issue
    postWebhookJson(
        "{\"issue-id\": \"10002\", \"transition\": \"reopen\" }",
        "92bbd2be7524447cbd7d8f8e63c8eb7b88c29777");

    // Check states of all tickets
    assertEquals(issueA.fields.status.id(), getIssue("10000").fields.status.id()); // unchanged
    // assertEquals(config.getClosedStatusId(), getIssue("10001").fields.status.id()); // closed
    // assertEquals(config.getReopenedStatusId(), getIssue("10002").fields.status.id()); // reopened

    // Check correct handling of text fields into issue
    assertEquals("Example rule (example_user/example_repo)", issueA.fields.summary);

    String description =
        "*[Example rule|http://lgtm.example.com/rules/10000]*\n"
            + "\n"
            + "In {{/example.py}}:\n"
            + "{quote}Description of one issue.\n"
            + "Description of another issue.{quote}\n"
            + "[View alert on LGTM|http://lgtm.example.com/issues/10000/language/8cdXzW\\+PyA3qiHBbWFomoMGtiIE=]";

    assertEquals(description, issueA.fields.description);
  }

  private void createIssueType() throws UnsupportedEncodingException, IOException, JSONException {

    HttpPost httpPost = new HttpPost(baseUrl + "/rest/api/2/issuetype");
    httpPost.setHeader("Content-type", "application/json");
    httpPost.setHeader(
        "Authorization",
        "Basic "
            + Base64.getEncoder().encodeToString(("admin:admin").getBytes(StandardCharsets.UTF_8)));

    String jsonString =
        new JSONObject()
            .put("name", "LGTM alert")
            .put("description", "Issue type for managing LGTM alerts")
            .put("type", "standard")
            .toString();

    httpPost.setEntity(new StringEntity(jsonString));

    HttpResponse httpResponse = httpClient.execute(httpPost);

    assertEquals("Failed to create issue type", 201, httpResponse.getStatusLine().getStatusCode());
  }

  private Config configurePlugin() throws ClientProtocolException, IOException {

    config = new Config();
    config.setKey("webhook");
    config.setLgtmSecret("12345678");
    config.setUsername("admin");
    config.setProjectKey("MKY");
    config.setPriorityLevelId("3");

    HttpPut httpPut = new HttpPut(baseUrl + "/rest/lgtm-config/1.0/");
    httpPut.setHeader("Content-type", "application/json");
    httpPut.setHeader(
        "Authorization",
        "Basic "
            + Base64.getEncoder().encodeToString(("admin:admin").getBytes(StandardCharsets.UTF_8)));

    httpPut.setEntity(new StringEntity(new GsonBuilder().create().toJson(config)));

    HttpResponse response = httpClient.execute(httpPut);

    assertEquals("Failed to configure plugin", 204, response.getStatusLine().getStatusCode());

    return config;
  }

  private Issue postWebhookJson(String jsonString, String signature)
      throws ClientProtocolException, IOException, JSONException {

    HttpPost httpPost = new HttpPost(baseUrl + "/plugins/servlet/lgtm/webhook");

    httpPost.setHeader("Content-type", "application/json");
    httpPost.setHeader("X-LGTM-Signature", signature);

    httpPost.setEntity(new StringEntity(jsonString));

    HttpResponse httpResponse = httpClient.execute(httpPost);

    assertThat(
        "POST request failed",
        httpResponse.getStatusLine().getStatusCode(),
        isIn(Arrays.asList(200, 201)));

    String responseJsonString =
        new String(EntityUtils.toByteArray(httpResponse.getEntity()), StandardCharsets.UTF_8);
    String issueId = new JSONObject(responseJsonString).getString("issue-id");
    return getIssue(issueId);
  }

  private Issue getIssue(String issueId) {
    return Iterables.getOnlyElement(
        testKit
            .search()
            .getSearch(new SearchRequest().fields("*all").jql("issue = " + issueId))
            .issues);
  }
}
