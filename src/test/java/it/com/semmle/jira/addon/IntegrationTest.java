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
import com.google.common.collect.Iterables;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.Response;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;

public class IntegrationTest {

  final String CREATE_STRING =
      "{\n"
          + "  \"transition\": \"create\",\n"
          + "  \"project\": {\n"
          + "    \"id\": 1000003,\n"
          + "    \"url-identifier\": \"px/project-x/project-x.js\",\n"
          + "    \"name\": \"project-x/project-x.js\",\n"
          + "    \"url\": \"https://lgtm.example.com/projects/px/project-x/project-x.js\"\n"
          + "  },\n"
          + "  \"alert\": {\n"
          + "    \"file\": \"/src/ng/compile.js\",\n"
          + "    \"message\": \"@param tag refers to non-existent parameter enabled.\\n\",\n"
          + "    \"url\": \"https://lgtm.example.com/issues/1000003/javascript/+ja0cf6+84AGgat15W1jooeMfUY=\",\n"
          + "    \"query\": {\n"
          + "      \"name\": \"JSDoc tag for non-existent parameter\",\n"
          + "      \"url\": \"https://lgtm.example.com/rules/1000507\"\n"
          + "    },\n"
          + "    \"suppressed\": false\n"
          + "  }\n"
          + "}";

  Backdoor testKit;
  String baseUrl;
  HttpClient httpClient;
  Config config;

  private Issue issueA;
  private Issue issueB;
  private Issue issueC;
  private Issue issueD;
  private Issue issueE;

  public IntegrationTest() throws ClientProtocolException, IOException {

    // Use TestKit to initialise a test instance of Jira
    testKit = new Backdoor(new TestKitLocalEnvironmentData());
    testKit.restoreBlankInstance(TimeBombLicence.LICENCE_FOR_TESTING);
    testKit.issueLinking().enable();

    testKit.usersAndGroups().addUser("developer", "password", "Developer", "developer@example.com");
    testKit.usersAndGroups().addUser("lgtm", "password", "LGTM", "lgtm@example.com");
    testKit.usersAndGroups().addUserToGroup("developer", "jira-developers");
    testKit.project().addProject("Apollo", "AP", "developer");

    baseUrl = testKit.generalConfiguration().getEnvironmentData().getBaseUrl().toString();
    httpClient = HttpClientBuilder.create().build();

    config = configurePlugin();
  }

  @Before
  public void setUp() throws IOException {
    Request createRequest = Util.JSON.readValue(CREATE_STRING, Request.class);
    // Create some issues, subsequent tests assume these have known IDs
    issueA = postWebhookJson(createRequest);
    assertEquals("IssueA id is wrong", "10000", issueA.id);
    issueB = postWebhookJson(createRequest);
    assertEquals("IssueB id is wrong", "10001", issueB.id);
    issueC = postWebhookJson(createRequest);
    assertEquals("IssueC id is wrong", "10002", issueC.id);
    issueD = postWebhookJson(createRequest);
    assertEquals("issueD id is wrong", "10003", issueD.id);
    issueE = postWebhookJson(createRequest);
    assertEquals("issueE id is wrong", "10004", issueE.id);
  }

  @Test
  public void testCreationAndTransitionOfIssues() throws IOException {

    // Check correct handling of text fields into issue
    assertEquals(
        "JSDoc tag for non-existent parameter (project-x/project-x.js)", issueA.fields.summary);
    String description =
        "*[JSDoc tag for non\\-existent parameter|https://lgtm.example.com/rules/1000507]*\n"
            + "\n"
            + "In {{/src/ng/compile.js}}:\n"
            + "{quote}@param tag refers to non\\-existent parameter enabled.{quote}\n"
            + "[View alert on LGTM|https://lgtm.example.com/issues/1000003/javascript/\\+ja0cf6\\+84AGgat15W1jooeMfUY=]";

    assertEquals(description, issueA.fields.description);
    assertEquals(Collections.singletonList("project-x/project-x.js"), issueA.fields.labels);

    // Close two issues
    postWebhookJson(new Request(Transition.CLOSE, Long.valueOf(issueB.id)));
    postWebhookJson(new Request(Transition.CLOSE, Long.valueOf(issueC.id)));

    // Reopen one issue
    postWebhookJson(new Request(Transition.REOPEN, Long.valueOf(issueC.id)));

    // Suppress two issues
    postWebhookJson(new Request(Transition.SUPPRESS, Long.valueOf(issueD.id)));
    postWebhookJson(new Request(Transition.SUPPRESS, Long.valueOf(issueE.id)));

    // Unsuppress one issue
    postWebhookJson(new Request(Transition.UNSUPPRESS, Long.valueOf(issueE.id)));

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
  public void testManualTransitionOfIssues() throws IOException {

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
    postWebhookJson(new Request(Transition.SUPPRESS, Long.valueOf(issueC.id)));

    // Should be unchanged
    assertEquals("Done", getIssue("10002").fields.status.name()); // close suppressed
    assertEquals("Won't Fix", getIssue("10002").fields.resolution.name); // close suppressed

    // Close suppressed issues
    postWebhookJson(new Request(Transition.CLOSE, Long.valueOf(issueA.id)));
    postWebhookJson(new Request(Transition.CLOSE, Long.valueOf(issueC.id)));

    // Check closed issues
    assertEquals("Done", getIssue("10000").fields.status.name()); // closed
    assertEquals("Fixed", getIssue("10000").fields.resolution.name); // closed

    assertEquals("Done", getIssue("10002").fields.status.name()); // closed
    assertEquals("Fixed", getIssue("10002").fields.resolution.name); // closed
  }

  private Config configurePlugin() throws ClientProtocolException, IOException {

    config = new Config();
    config.setKey("config_key");
    config.setLgtmSecret("12345678");
    config.setUsername("lgtm");
    config.setProjectKey("AP");

    HttpPut httpPut = new HttpPut(baseUrl + "/rest/lgtm-config/1.0/");
    httpPut.setHeader("Content-type", "application/json");
    httpPut.setHeader(
        "Authorization",
        "Basic "
            + Base64.getEncoder().encodeToString(("admin:admin").getBytes(StandardCharsets.UTF_8)));

    httpPut.setEntity(new StringEntity(Util.JSON.writeValueAsString(config)));
    HttpResponse response = httpClient.execute(httpPut);

    assertEquals("Failed to configure plugin", 204, response.getStatusLine().getStatusCode());
    HttpClientUtils.closeQuietly(response);

    return config;
  }

  private Issue postWebhookJson(Object request) throws ClientProtocolException, IOException {
    byte[] payload = Util.JSON.writeValueAsBytes(request);
    HttpPost httpPost = new HttpPost(baseUrl + "/plugins/servlet/lgtm/" + config.getKey());

    httpPost.setHeader("X-LGTM-Signature", Util.calculateHmac(config.getLgtmSecret(), payload));

    httpPost.setEntity(new ByteArrayEntity(payload, ContentType.APPLICATION_JSON));

    HttpResponse httpResponse = httpClient.execute(httpPost);

    assertThat(
        "POST request failed",
        httpResponse.getStatusLine().getStatusCode(),
        isIn(Arrays.asList(200, 201)));

    Response response = Util.JSON.readValue(httpResponse.getEntity().getContent(), Response.class);
    String issueId = response.issueId;
    HttpClientUtils.closeQuietly(httpResponse);

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
