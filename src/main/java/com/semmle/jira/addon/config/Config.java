package com.semmle.jira.addon.config;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
import java.net.URI;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
@XmlAccessorType(XmlAccessType.FIELD)
public class Config {
  @XmlElement private String key;
  @XmlElement private String lgtmSecret;
  @XmlElement private String username;
  @XmlElement private String projectKey;
  @XmlElement private String priorityLevelId;
  @XmlElement private URI externalHookUrl;
  @XmlElement private String trackerKey;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getLgtmSecret() {
    return lgtmSecret;
  }

  public void setLgtmSecret(String lgtmSecret) {
    this.lgtmSecret = lgtmSecret;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getProjectKey() {
    return projectKey;
  }

  public void setProjectKey(String projectKey) {
    this.projectKey = projectKey;
  }

  public String getPriorityLevelId() {
    return priorityLevelId;
  }

  public void setPriorityLevelId(String priorityLevelId) {
    this.priorityLevelId = priorityLevelId;
  }

  public URI getExternalHookUrl() {
    return externalHookUrl;
  }

  public void setExternalHookUrl(URI url) {
    this.externalHookUrl = url;
  }

  public String getTrackerKey() {
    return trackerKey;
  }

  public void setTrackerKey(String trackerKey) {
    this.trackerKey = trackerKey;
  }

  public static Config get(
      String configKey,
      TransactionTemplate transactionTemplate,
      PluginSettingsFactory pluginSettingsFactory) {
    return transactionTemplate.execute(
        new TransactionCallback<Config>() {
          public Config doInTransaction() {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            Config config = new Config();
            config.setKey(configKey);
            config.setLgtmSecret(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".lgtmSecret"));
            config.setUsername(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".username"));
            config.setProjectKey(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".projectKey"));
            config.setPriorityLevelId(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".priorityLevelId"));
            String externalHook =
                (String) settings.get("com.lgtm.addon.config." + configKey + ".externalHookUrl");
            config.setExternalHookUrl(externalHook == null ? null : URI.create(externalHook));
            config.setTrackerKey(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".trackerKey"));
            return config;
          }
        });
  }

  public static void put(
      Config config,
      TransactionTemplate transactionTemplate,
      PluginSettingsFactory pluginSettingsFactory) {
    transactionTemplate.execute(
        new TransactionCallback<Void>() {
          public Void doInTransaction() {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".lgtmSecret", config.getLgtmSecret());
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".username", config.getUsername());
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".projectKey", config.getProjectKey());
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".priorityLevelId",
                config.getPriorityLevelId());
            URI externalHook = config.getExternalHookUrl();
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".externalHookUrl",
                externalHook == null ? null : externalHook.toString());
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".trackerKey", config.getTrackerKey());
            return null;
          }
        });
  }
}
