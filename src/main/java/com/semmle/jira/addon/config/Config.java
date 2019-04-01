package com.semmle.jira.addon.config;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.net.URI;
import java.util.Properties;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonProperty;

public class Config {
  public enum Error {
    MISSING_CONFIG_KEY,
    MISSING_SECRET,
    MISSING_USERNAME,
    MISSING_PROJECT_KEY,
    USER_NOT_FOUND,
    PROJECT_NOT_FOUND
  }

  private static final String PROPERTY_NAME_KEY = "key";
  private static final String PROPERTY_NAME_LGTM_SECRET = "lgtmSecret";
  private static final String PROPERTY_NAME_USERNAME = "username";
  private static final String PROPERTY_NAME_PROJECT_KEY = "projectKey";
  private static final String PROPERTY_NAME_EXTERNAL_HOOK_URL = "externalHookUrl";
  private static final String PROPERTY_NAME_TRACKER_KEY = "trackerKey";

  private final Properties properties;

  public Config() {
    properties = new Properties();
  }

  public Config(Properties properties) {
    if (properties == null) {
      this.properties = new Properties();
    } else {
      this.properties = properties;
    }
  }

  @JsonProperty
  public String getKey() {
    return properties.getProperty(PROPERTY_NAME_KEY);
  }

  @JsonProperty
  public void setKey(String key) {
    this.properties.put(PROPERTY_NAME_KEY, key);
  }

  @JsonProperty
  public String getLgtmSecret() {
    return properties.getProperty(PROPERTY_NAME_LGTM_SECRET);
  }

  @JsonProperty
  public void setLgtmSecret(String lgtmSecret) {
    this.properties.put(PROPERTY_NAME_LGTM_SECRET, lgtmSecret);
  }

  @JsonProperty
  public String getUsername() {
    return properties.getProperty(PROPERTY_NAME_USERNAME);
  }

  @JsonProperty
  public void setUsername(String username) {
    this.properties.put(PROPERTY_NAME_USERNAME, username);
  }

  @JsonProperty
  public String getProjectKey() {
    return properties.getProperty(PROPERTY_NAME_PROJECT_KEY);
  }

  @JsonProperty
  public void setProjectKey(String projectKey) {
    this.properties.put(PROPERTY_NAME_PROJECT_KEY, projectKey);
  }

  @JsonProperty
  public URI getExternalHookUrl() {
    if (properties.containsKey(PROPERTY_NAME_EXTERNAL_HOOK_URL))
      return URI.create(properties.getProperty(PROPERTY_NAME_EXTERNAL_HOOK_URL));
    return null;
  }

  @JsonProperty
  public void setExternalHookUrl(URI externalHookUrl) {
    if (externalHookUrl == null) this.properties.put(PROPERTY_NAME_EXTERNAL_HOOK_URL, null);
    else this.properties.put(PROPERTY_NAME_EXTERNAL_HOOK_URL, externalHookUrl.toString());
  }

  @JsonProperty
  public String getTrackerKey() {
    return properties.getProperty(PROPERTY_NAME_TRACKER_KEY);
  }

  @JsonProperty
  public void setTrackerKey(String trackerKey) {
    this.properties.put(PROPERTY_NAME_TRACKER_KEY, trackerKey);
  }

  public Error validate() {
    if (getKey() == null) {
      return Error.MISSING_CONFIG_KEY;
    }

    if (getLgtmSecret() == null) {
      return Error.MISSING_SECRET;
    }

    if (getUsername() != null) {
      ApplicationUser user = ComponentAccessor.getUserManager().getUserByName(getUsername());
      if (user == null) return Error.USER_NOT_FOUND;
    } else {
      return Error.MISSING_USERNAME;
    }

    if (getProjectKey() != null) {
      Project project =
          ComponentAccessor.getProjectManager().getProjectByCurrentKey(getProjectKey());
      if (project == null) return Error.PROJECT_NOT_FOUND;
    } else {
      return Error.MISSING_PROJECT_KEY;
    }

    return null;
  }

  @JsonIgnore
  public ApplicationUser getUser() {
    return ComponentAccessor.getUserManager().getUserByName(getUsername());
  }

  @JsonIgnore
  public Project getProject() {
    return ComponentAccessor.getProjectManager().getProjectByCurrentKey(getProjectKey());
  }

  public static Config get(String configKey) {
    PluginSettingsFactory pluginSettingsFactory =
        ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
    Config config = new Config((Properties) settings.get("com.lgtm.addon.config." + configKey));
    return config;
  }

  public static void put(Config config) {
    PluginSettingsFactory pluginSettingsFactory =
        ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
    PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
    settings.put("com.lgtm.addon.config." + config.getKey(), config.properties);
  }
}
