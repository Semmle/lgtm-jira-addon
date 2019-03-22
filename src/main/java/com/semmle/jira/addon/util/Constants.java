package com.semmle.jira.addon.util;

public class Constants {
  public static final String PLUGIN_KEY = "com.semmle.lgtm-jira-addon";

  public static final String WORKFLOW_NAME = "LGTM alert";
  public static final String ISSUE_TYPE_NAME = "LGTM alert";

  public static final String WORKFLOW_REOPEN_TRANSITION_NAME = "LGTM.Reopen";
  public static final String WORKFLOW_CLOSE_TRANSITION_NAME = "LGTM.Close";
  public static final String WORKFLOW_SUPPRESS_TRANSITION_NAME = "LGTM.Suppress";

  public static final String CUSTOM_FIELD_NAME = "LGTM Config Key";
  public static final String LGTM_PAYLOAD_PROPERTY = "lgtm.request.payload";

  public static final String CONFIGURED_VERSION_KEY = "com.lgtm.addon.version";

  public static final String CUSTOM_FIELD_CONFIG_KEY =
      "com.lgtm.addon.config.configKeyCustomFieldId";
}
