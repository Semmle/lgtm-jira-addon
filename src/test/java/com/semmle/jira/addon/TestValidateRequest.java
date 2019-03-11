package com.semmle.jira.addon;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.status.Status;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.semmle.jira.addon.config.Config;
import com.semmle.jira.addon.config.ProcessedConfig;
import java.io.IOException;
import java.util.Arrays;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Matchers;

public class TestValidateRequest extends TestLgtmServletBase {
  @AvailableInContainer private UserManager userManager = mock(UserManager.class);
  @AvailableInContainer private ProjectManager projectManager = mock(ProjectManager.class);
  @AvailableInContainer private ConstantsManager constantsManager = mock(ConstantsManager.class);

  Config config;

  @Before
  public void initTests() {
    // UserManager
    ApplicationUser user = mock(ApplicationUser.class);
    when(user.getName()).thenReturn("UserName");
    when(userManager.getUserByName(any(String.class))).thenReturn(user);

    // Project Manager
    com.atlassian.jira.project.Project project = mock(com.atlassian.jira.project.Project.class);
    when(project.getId()).thenReturn(1l);
    when(projectManager.getProjectByCurrentKey(any(String.class))).thenReturn(project);

    // Issue Type
    IssueType issueType = mock(IssueType.class);
    when(issueType.getName()).thenReturn("LGTM alert");

    when(project.getIssueTypes()).thenReturn(Arrays.asList(issueType));

    // Constants Manager
    when(constantsManager.getIssueType(any(String.class))).thenReturn(issueType);
    Status status = mock(Status.class);
    when(constantsManager.getStatus(any(String.class))).thenReturn(status);

    // Config
    setupConfig();
    when(transactionTemplate.execute(Matchers.<TransactionCallback<Config>>any()))
        .thenReturn(config);
  }

  @Test
  public void testValidateRequestNoConfig() throws IOException {
    String lgtmSignature = "";
    byte[] bytes = "".getBytes();
    String configKey = "";

    HttpServletResponse resp = mockResponse();

    config.setLgtmSecret(null); // Mocking the configuration is not set

    ProcessedConfig processedConfig =
        servlet.validateRequest(lgtmSignature, bytes, resp, configKey);

    verify(resp).setStatus(500);
    verify(resp.getWriter())
        .write("{\"code\":500,\"error\":\"Configuration needed – see documentation.\"}");
  }

  @Test
  public void testValidateRequestForbidden() throws IOException {
    String lgtmSignature = "";
    byte[] bytes = "".getBytes();
    String configKey = "";

    HttpServletResponse resp = mockResponse();

    ProcessedConfig processedConfig =
        servlet.validateRequest(lgtmSignature, bytes, resp, configKey);

    verify(resp).setStatus(403);
    verify(resp.getWriter()).write("{\"code\":403,\"error\":\"Forbidden.\"}");
  }

  @Test
  public void testValidateRequestSuccess() throws IOException {
    String lgtmSignature = "";
    byte[] bytes = "".getBytes();
    String configKey = "";

    HttpServletResponse resp = mockResponse();

    lgtmSignature = "0caf649feee4953d87bf903ac1176c45e028df16";
    bytes = "message".getBytes();

    ProcessedConfig processedConfig =
        servlet.validateRequest(lgtmSignature, bytes, resp, configKey);

    assertNotNull(processedConfig);
  }

  private void setupConfig() {
    config = new Config();
    config.setKey("key");
    config.setLgtmSecret("secret");
    config.setUsername("username");
    config.setProjectKey("projectKey");
  }
}
