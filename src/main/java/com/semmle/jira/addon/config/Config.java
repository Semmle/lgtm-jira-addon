package com.semmle.jira.addon.config;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.net.URI;
import java.util.Map;
import java.util.Properties;

public class Config {
  public static final String PROPERTY_NAME_KEY = "key";
  public static final String PROPERTY_NAME_LGTM_SECRET = "lgtmSecret";
  public static final String PROPERTY_NAME_USERNAME = "username";
  public static final String PROPERTY_NAME_PROJECT_KEY = "projectKey";
  public static final String PROPERTY_NAME_EXTERNAL_HOOK_URL = "externalHookUrl";
  public static final String PROPERTY_NAME_TRACKER_KEY = "trackerKey";

  private Properties properties = new Properties();

  public Config() {}

  public Config(Map<String, String> configMap) {
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

  public static Config get(String configKey) {

    TransactionTemplate transactionTemplate =
        ComponentAccessor.getOSGiComponentInstanceOfType(TransactionTemplate.class);
    PluginSettingsFactory pluginSettingsFactory =
        ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
    return transactionTemplate.execute(
        new TransactionCallback<Config>() {
          public Config doInTransaction() {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            Config config = new Config();
            config.properties = (Properties) settings.get("com.lgtm.addon.config." + configKey);
            return config;
          }
        });
  }

  public static void put(Config config) {
    TransactionTemplate transactionTemplate =
        ComponentAccessor.getOSGiComponentInstanceOfType(TransactionTemplate.class);
    PluginSettingsFactory pluginSettingsFactory =
        ComponentAccessor.getOSGiComponentInstanceOfType(PluginSettingsFactory.class);
    transactionTemplate.execute(
        new TransactionCallback<Void>() {
          public Void doInTransaction() {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            settings.put("com.lgtm.addon.config." + config.getKey(), config.properties);
            return null;
          }
        });
  }
}
