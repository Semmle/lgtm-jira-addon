package com.semmle.jira.addon.workflow;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService;
import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService.CreateValidationResult;
import com.atlassian.jira.bc.issue.link.RemoteIssueLinkService.RemoteIssueLinkResult;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.link.IssueLinkManager;
import com.atlassian.jira.issue.link.RemoteIssueLink;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.junit.rules.MockitoContainer;
import com.atlassian.jira.junit.rules.MockitoMocksInContainer;
import com.atlassian.jira.mock.workflow.MockWorkflowContext;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.user.util.UserManager;
import com.opensymphony.workflow.WorkflowException;
import com.semmle.jira.addon.Request;
import com.semmle.jira.addon.Request.Alert;
import com.semmle.jira.addon.Request.Alert.Query;
import com.semmle.jira.addon.Request.Project;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.Util;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.codehaus.jackson.JsonNode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class LgtmIssueLinkFunctionTest {
  @Rule public MockitoContainer mockitoContainer = MockitoMocksInContainer.rule(this);

  @AvailableInContainer private IssueLinkManager issueLinkManager = mock(IssueLinkManager.class);

  @AvailableInContainer private UserManager userManager = mock(UserManager.class);

  @AvailableInContainer
  private PermissionManager permissionsManager = mock(PermissionManager.class);

  @AvailableInContainer
  private RemoteIssueLinkService issueLinkService = mock(RemoteIssueLinkService.class);

  private LgtmIssueLinkFunction function;
  private MutableIssue issue;

  private ApplicationUser user;

  @Before
  public void setup() {
    when(issueLinkManager.isLinkingEnabled()).thenReturn(true);
    issue = mock(MutableIssue.class);
    when(issue.getId()).thenReturn(10L);
    user = mock(ApplicationUser.class);
    when(user.getKey()).thenReturn("userkey");
    when(userManager.getUserByKey(user.getKey())).thenReturn(user);
    com.atlassian.jira.project.Project project = mock(com.atlassian.jira.project.Project.class);
    when(issue.getProjectObject()).thenReturn(project);
    when(permissionsManager.hasPermission(ProjectPermissions.LINK_ISSUES, project, user))
        .thenReturn(true);
    function = new LgtmIssueLinkFunction();
  }

  @Test
  public void testExecute() throws WorkflowException {
    CreateValidationResult validationResult = mock(CreateValidationResult.class);
    when(issueLinkService.validateCreate(any(), any())).thenReturn(validationResult);
    when(validationResult.isValid()).thenReturn(true);
    RemoteIssueLinkResult linkResult = mock(RemoteIssueLinkResult.class);
    when(issueLinkService.create(any(), any())).thenReturn(linkResult);
    when(linkResult.isValid()).thenReturn(true);

    Map<String, Object> transientVars = new LinkedHashMap<>();
    Project project =
        new Project(1L, "example/project", "Example project", "http://lgtm.example.com/projects/1");
    Query query = new Query("Query name", "http://lgtm.example.com/rules/1");
    Alert alert = new Alert("file", "some error", "http://lgtm.example.com/issues/...", query);
    Request request = new Request(Transition.CREATE, null, project, alert);
    JsonNode json = Util.JSON.valueToTree(request);

    transientVars.put("context", new MockWorkflowContext(user.getKey()));
    transientVars.put("issue", issue);
    transientVars.put(
        "issueProperties", Collections.singletonMap(Constants.LGTM_PAYLOAD_PROPERTY, json));
    function.execute(transientVars, Collections.emptyMap(), null);

    verify(issueLinkService)
        .validateCreate(
            any(),
            argThat(
                (RemoteIssueLink x) -> {
                  Assert.assertEquals("LGTM", x.getApplicationName());
                  Assert.assertEquals("com.lgtm", x.getApplicationType());
                  Assert.assertEquals(alert.url, x.getUrl());
                  Assert.assertEquals(issue.getId(), x.getIssueId());
                  Assert.assertEquals("causes", x.getRelationship());
                  Assert.assertEquals(alert.query.name, x.getTitle());
                  return true;
                }));
  }
}
