package com.semmle.jira.addon.config;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Config {
  public enum Error {
    MISSING_CONFIG_KEY,
    MISSING_SECRET,
    MISSING_USERNAME,
    MISSING_PROJECT_KEY,
    USER_NOT_FOUND,
    PROJECT_NOT_FOUND
  }

  public static final String PROPERTY_NAME_KEY = "key";
  public static final String PROPERTY_NAME_LGTM_SECRET = "lgtmSecret";
  public static final String PROPERTY_NAME_USERNAME = "username";
  public static final String PROPERTY_NAME_PROJECT_KEY = "projectKey";
  public static final String PROPERTY_NAME_EXTERNAL_HOOK_URL = "externalHookUrl";
  public static final String PROPERTY_NAME_TRACKER_KEY = "trackerKey";

  private final Properties properties;

  public Config() {
    properties = new Properties();
  }

  public Config(Map<String, String> configMap) {
    properties = new Properties();
    if (configMap.containsKey(PROPERTY_NAME_KEY))
      properties.setProperty(PROPERTY_NAME_KEY, configMap.get(PROPERTY_NAME_KEY));

    if (configMap.containsKey(PROPERTY_NAME_LGTM_SECRET))
      properties.setProperty(PROPERTY_NAME_LGTM_SECRET, configMap.get(PROPERTY_NAME_LGTM_SECRET));

    if (configMap.containsKey(PROPERTY_NAME_USERNAME))
      properties.setProperty(PROPERTY_NAME_USERNAME, configMap.get(PROPERTY_NAME_USERNAME));

    if (configMap.containsKey(PROPERTY_NAME_PROJECT_KEY))
      properties.setProperty(PROPERTY_NAME_PROJECT_KEY, configMap.get(PROPERTY_NAME_PROJECT_KEY));

    if (configMap.containsKey(PROPERTY_NAME_EXTERNAL_HOOK_URL))
      properties.setProperty(
          PROPERTY_NAME_EXTERNAL_HOOK_URL, configMap.get(PROPERTY_NAME_EXTERNAL_HOOK_URL));

    if (configMap.containsKey(PROPERTY_NAME_TRACKER_KEY))
      properties.setProperty(PROPERTY_NAME_TRACKER_KEY, configMap.get(PROPERTY_NAME_TRACKER_KEY));
  }

  public Config(Properties properties) {
	  if (properties == null) {
		  this.properties = new Properties();
	  } else {
		  this.properties = properties;
	  }
  }

  public String getKey() {
    return properties.getProperty(PROPERTY_NAME_KEY);
  }

  public void setKey(String key) {
    this.properties.put(PROPERTY_NAME_KEY, key);
  }

  public String getLgtmSecret() {
    return properties.getProperty(PROPERTY_NAME_LGTM_SECRET);
  }

  public void setLgtmSecret(String lgtmSecret) {
    this.properties.put(PROPERTY_NAME_LGTM_SECRET, lgtmSecret);
  }

  public String getUsername() {
    return properties.getProperty(PROPERTY_NAME_USERNAME);
  }

  public void setUsername(String username) {
    this.properties.put(PROPERTY_NAME_USERNAME, username);
  }

  public String getProjectKey() {
    return properties.getProperty(PROPERTY_NAME_PROJECT_KEY);
  }

  public void setProjectKey(String projectKey) {
    this.properties.put(PROPERTY_NAME_PROJECT_KEY, projectKey);
  }

  public URI getExternalHookUrl() {
    return URI.create(properties.getProperty("externalHookUrl"));
  }

  public void setExternalHookUrl(String hookUrl) {
    this.properties.put(PROPERTY_NAME_EXTERNAL_HOOK_URL, hookUrl);
  }

  public String getTrackerKey() {
    return properties.getProperty(PROPERTY_NAME_TRACKER_KEY);
  }

  public void setTrackerKey(String trackerKey) {
    this.properties.put(PROPERTY_NAME_TRACKER_KEY, trackerKey);
  }

  public List<Error> validate() {
    List<Error> errors = new ArrayList<Error>();
    if (getKey() == null) {
      errors.add(Error.MISSING_CONFIG_KEY);
    }

    if (getLgtmSecret() == null) {
      errors.add(Error.MISSING_SECRET);
    }

    if (getUsername() != null) {
      ApplicationUser user = ComponentAccessor.getUserManager().getUserByName(getUsername());
      if (user == null) errors.add(Error.USER_NOT_FOUND);
    } else {
      errors.add(Error.MISSING_USERNAME);
    }

    if (getProjectKey() != null) {
      Project project =
          ComponentAccessor.getProjectManager().getProjectByCurrentKey(getProjectKey());
      if (project == null) errors.add(Error.PROJECT_NOT_FOUND);
    } else {
      errors.add(Error.MISSING_PROJECT_KEY);
    }

    return errors;
  }

  public ApplicationUser getUser() {
    return ComponentAccessor.getUserManager().getUserByName(getUsername());
  }

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
