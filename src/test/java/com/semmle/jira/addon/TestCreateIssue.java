package com.semmle.jira.addon;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.atlassian.jira.bc.issue.IssueService.CreateValidationResult;
import com.atlassian.jira.bc.issue.IssueService.IssueResult;
import com.atlassian.jira.config.ConstantsManager;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItem;
import com.atlassian.jira.config.managedconfiguration.ManagedConfigurationItemService;
import com.atlassian.jira.issue.CustomFieldManager;
import com.atlassian.jira.issue.IssueInputParameters;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.layout.field.FieldLayout;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutItem;
import com.atlassian.jira.issue.fields.layout.field.FieldLayoutManager;
import com.atlassian.jira.issue.issuetype.IssueType;
import com.atlassian.jira.issue.label.LabelManager;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ErrorCollection;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.semmle.jira.addon.LgtmServlet.CreateIssueException;
import com.semmle.jira.addon.Request.Alert;
import com.semmle.jira.addon.Request.Alert.Query;
import com.semmle.jira.addon.Request.Project;
import com.semmle.jira.addon.Request.Transition;
import com.semmle.jira.addon.config.ProcessedConfig;
import com.semmle.jira.addon.util.Constants;
import com.semmle.jira.addon.util.CustomFieldRetrievalException;
import com.semmle.jira.addon.util.Util;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.junit.Before;
import org.junit.Test;

public class TestCreateIssue extends TestCreateAndTransitionBase {

  private static final Long CUSTOM_FIELD_ID = 43L;
  private static final String CONFIG_KEY = "config_key";

  @AvailableInContainer private ConstantsManager constantsManager = mock(ConstantsManager.class);
  @AvailableInContainer private LabelManager labelManager = mock(LabelManager.class);

  @AvailableInContainer
  private ManagedConfigurationItemService managedConfigurationItemService =
      mock(ManagedConfigurationItemService.class);

  @AvailableInContainer
  private CustomFieldManager customFieldManager = mock(CustomFieldManager.class);

  @AvailableInContainer
  private FieldLayoutManager fieldLayoutManager = mock(FieldLayoutManager.class);

  @AvailableInContainer
  PluginSettingsFactory pluginSettingsFactory = mock(PluginSettingsFactory.class);

  protected MockPluginSettings pluginSettings = new MockPluginSettings();

  CreateValidationResult createValidationResult = mock(CreateValidationResult.class);
  ProcessedConfig config = mock(ProcessedConfig.class);

  private IssueResult issueResult = mock(IssueResult.class);
  private CustomField customField = mock(CustomField.class);
  private MutableIssue issue = mock(MutableIssue.class);
  private FieldLayoutItem fieldLayoutItem = mock(FieldLayoutItem.class);

  public static Request createRequest(
      String projectName, String queryName, String alertFile, String alertMessage) {
    Transition transition = Transition.CREATE;
    String url = "www." + projectName + ".com";
    Project project = new Project(1l, "g/" + projectName, projectName, url);
    Query query = new Query(queryName, url + "/" + queryName);
    Alert alert = new Alert(alertFile, alertMessage, url + "/alert", query);

    return new Request(transition, 1l, project, alert);
  }

  @Before
  public void initTests() {
    super.initTests();

    when(pluginSettingsFactory.createGlobalSettings()).thenReturn(pluginSettings);

    IssueType lgtmIssueType = mock(IssueType.class);
    when(lgtmIssueType.getName()).thenReturn(Constants.ISSUE_TYPE_NAME);
    when(lgtmIssueType.getId()).thenReturn("1");
    when(constantsManager.getIssueConstantByName(
            eq(ConstantsManager.CONSTANT_TYPE.ISSUE_TYPE.getType()), anyString()))
        .thenReturn(lgtmIssueType);

    when(customField.getIdAsLong()).thenReturn(CUSTOM_FIELD_ID);

    when(customFieldManager.getCustomFieldObject(CUSTOM_FIELD_ID)).thenReturn(customField);

    when(issueService.validateCreate(any(ApplicationUser.class), any(IssueInputParameters.class)))
        .thenReturn(createValidationResult);

    ErrorCollection errorCollection = mock(ErrorCollection.class);
    when(createValidationResult.getErrorCollection()).thenReturn(errorCollection);
    when(createValidationResult.getIssue()).thenReturn(issue);

    when(issueService.create(any(), any())).thenReturn(issueResult);
    when(issueResult.getIssue()).thenReturn(issue);

    when(issue.getId()).thenReturn(1l);

    ApplicationUser user = mock(ApplicationUser.class);
    when(config.getUser()).thenReturn(user);
    com.atlassian.jira.project.Project project = mock(com.atlassian.jira.project.Project.class);
    when(config.getProject()).thenReturn(project);
    when(config.getKey()).thenReturn(CONFIG_KEY);

    ManagedConfigurationItem managedField = mock(ManagedConfigurationItem.class);
    when(managedConfigurationItemService.getManagedCustomField(customField))
        .thenReturn(managedField);

    pluginSettings.put(Constants.CUSTOM_FIELD_CONFIG_KEY, (Object) CUSTOM_FIELD_ID.toString());

    FieldLayout fieldLayout = mock(FieldLayout.class);
    when(fieldLayoutManager.getFieldLayout(issueResult.getIssue())).thenReturn(fieldLayout);
    when(fieldLayout.getFieldLayoutItem(customField)).thenReturn(fieldLayoutItem);
  }

  @Test
  public void testCreateIssueSuccess()
      throws IOException, IllegalArgumentException, CustomFieldRetrievalException,
          CreateIssueException {
    HttpServletResponse resp = mockResponse();
    Request request = createRequest("test", "Query", "test.cpp", "Security Error");

    when(createValidationResult.getErrorCollection().hasAnyErrors()).thenReturn(false);
    when(issueResult.isValid()).thenReturn(true);
    Long issueId = servlet.createIssue(Util.JSON.valueToTree(request), request, config);
    Long expected = 1l;
    assertEquals(expected, issueId);

    verify(issue).setCustomFieldValue(customField, config.getKey());
  }

  @Test(expected = CreateIssueException.class)
  public void testCreateIssueFailure()
      throws IOException, IllegalArgumentException, CustomFieldRetrievalException,
          CreateIssueException {
    HttpServletResponse resp = mockResponse();
    Request request = createRequest("test", "Query", "test.cpp", "Security Error");

    when(createValidationResult.getErrorCollection().hasAnyErrors()).thenReturn(true);

    servlet.createIssue(Util.JSON.valueToTree(request), request, config);
  }
}
