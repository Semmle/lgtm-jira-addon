package it.com.semmle.jira.addon;

import static org.hamcrest.Matchers.isIn;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import com.atlassian.jira.testkit.client.Backdoor;
import com.atlassian.jira.testkit.client.restclient.Issue;
import com.atlassian.jira.testkit.client.restclient.SearchRequest;
import com.atlassian.jira.testkit.client.util.TestKitLocalEnvironmentData;
import com.atlassian.jira.testkit.client.util.TimeBombLicence;
import com.atlassian.jira.util.json.JSONException;
import com.atlassian.jira.util.json.JSONObject;
import com.google.common.collect.Iterables;
import com.semmle.jira.addon.Util;
import com.semmle.jira.addon.config.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.Test;

public class IntegrationTest {

  final String CREATE_STRING =
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
  final String CREATE_SIGNATURE = "a520128c3068f74c6d0f54d266dd0b2fe6634c33";

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

    reenablePlugin();
    config = configurePlugin();
  }

  @Test
  public void testCreationAndTransitionOfIssues() throws IOException, JSONException {
    // Create three issues, subsequent tests assume these have known IDs
    Issue issueA = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("IssueA id is wrong", "10000", issueA.id);
    Issue issueB = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("IssueB id is wrong", "10001", issueB.id);
    Issue issueC = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("IssueC id is wrong", "10002", issueC.id);
    Issue issueD = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("issueD id is wrong", "10003", issueD.id);
    Issue issueE = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("issueE id is wrong", "10004", issueE.id);

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

    // Suppress two issues
    postWebhookJson(
        "{\"issue-id\": \"10003\", \"transition\": \"suppress\" }",
        "f184bb5ba1853dc0715ba41e0ba1ae79d582d438");
    postWebhookJson(
        "{\"issue-id\": \"10004\", \"transition\": \"suppress\" }",
        "3f7dfc9b322fc1da13c3e04f581422991d45e020");

    // Unsuppress one issue
    postWebhookJson(
        "{\"issue-id\": \"10004\", \"transition\": \"unsuppress\" }",
        "d1e4593427491478c5a82bd87a3294cc84a33cf4");

    // Check states of all tickets
    assertEquals(issueA.fields.status.id(), getIssue("10000").fields.status.id()); // unchanged

    assertEquals("Done", getIssue("10001").fields.status.name()); // closed
    assertEquals("Fixed", getIssue("10001").fields.resolution.name); // closed

    assertEquals("To Do", getIssue("10002").fields.status.name()); // reopened
    assertNull(getIssue("10002").fields.resolution); // reopened

    assertEquals("In Review", getIssue("10003").fields.status.name()); // suppressed
    assertEquals("Won't Fix", getIssue("10003").fields.resolution.name); // suppressed

    assertEquals("To Do", getIssue("10004").fields.status.name()); // unsuppressed
    assertNull(getIssue("10004").fields.resolution); // unsuppressed
  }

  @Test
  public void testManualTransitionOfIssues() throws IOException, JSONException {
    // Create three issues, subsequent tests assume these have known IDs
    Issue issueA = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("IssueA id is wrong", "10000", issueA.id);
    Issue issueB = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("IssueB id is wrong", "10001", issueB.id);
    Issue issueC = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("IssueC id is wrong", "10002", issueC.id);
    Issue issueD = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("issueD id is wrong", "10003", issueD.id);
    Issue issueE = postWebhookJson(CREATE_STRING, CREATE_SIGNATURE);
    assertEquals("issueE id is wrong", "10004", issueE.id);

    // Suppress 3 issues
    testKit.issues().transitionIssue("10000", 51);
    testKit.issues().transitionIssue("10001", 51);
    testKit.issues().transitionIssue("10002", 51);

    // Reopen 1 issue
    testKit.issues().transitionIssue("10001", 61);

    // Close 1 issue
    testKit.issues().transitionIssue("10002", 71);

    // Check states of all tickets
    assertEquals("In Review", getIssue("10000").fields.status.name()); // suppressed
    assertEquals("Won't Fix", getIssue("10000").fields.resolution.name); // suppressed

    assertEquals("To Do", getIssue("10001").fields.status.name()); // reopened
    assertNull(getIssue("10001").fields.resolution); // reopened

    assertEquals("Done", getIssue("10002").fields.status.name()); // close suppressed
    assertEquals("Won't Fix", getIssue("10002").fields.resolution.name); // close suppressed

    // Send suppresion to close suppressed
    postWebhookJson(
        "{\"issue-id\": \"10002\", \"transition\": \"suppress\" }",
        "ae581e61e472e9e1daf949085c8fc86b3f40cb68");
    // Should be unchanged
    assertEquals("Done", getIssue("10002").fields.status.name()); // close suppressed
    assertEquals("Won't Fix", getIssue("10002").fields.resolution.name); // close suppressed

    // Close suppressed issues
    postWebhookJson(
        "{\"issue-id\": \"10000\", \"transition\": \"close\" }",
        "7a55296797b3db7ce9d3ce23c34d0fea8954a309");
    postWebhookJson(
        "{\"issue-id\": \"10002\", \"transition\": \"close\" }",
        "6bedc9b786705787c1522118f5723c94122d5200");

    // Check closed issues
    assertEquals("Done", getIssue("10000").fields.status.name()); // closed
    assertEquals("Fixed", getIssue("10000").fields.resolution.name); // closed

    assertEquals("Done", getIssue("10002").fields.status.name()); // closed
    assertEquals("Fixed", getIssue("10002").fields.resolution.name); // closed
  }

  private Config configurePlugin() throws ClientProtocolException, IOException {

    config = new Config();
    config.setKey("webhook");
    config.setLgtmSecret("12345678");
    config.setUsername("admin");
    config.setProjectKey("MKY");

    HttpPut httpPut = new HttpPut(baseUrl + "/rest/lgtm-config/1.0/");
    httpPut.setHeader("Content-type", "application/json");
    httpPut.setHeader(
        "Authorization",
        "Basic "
            + Base64.getEncoder().encodeToString(("admin:admin").getBytes(StandardCharsets.UTF_8)));

    httpPut.setEntity(new StringEntity(Util.JSON.writeValueAsString(config)));

    HttpResponse response = httpClient.execute(httpPut);

    assertEquals("Failed to configure plugin", 204, response.getStatusLine().getStatusCode());

    return config;
  }

  private void reenablePlugin() throws ClientProtocolException, IOException {
    HttpResponse resp;
    resp =
        httpClient.execute(
            new HttpDelete(baseUrl + "/rest/qr/1.0/plugin/enabled/com.semmle.lgtm-jira-addon"));
    resp.getEntity().getContent().close();

    resp =
        httpClient.execute(
            new HttpPost(baseUrl + "/rest/qr/1.0/plugin/enabled/com.semmle.lgtm-jira-addon"));
    resp.getEntity().getContent().close();
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
