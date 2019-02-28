package com.semmle.jira.addon.config;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.atlassian.sal.api.transaction.TransactionCallback;
import com.atlassian.sal.api.transaction.TransactionTemplate;
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
  @XmlElement private String closedStatusId;
  @XmlElement private String reopenedStatusId;
  @XmlElement private String priorityLevelId;

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

  public String getClosedStatusId() {
    return closedStatusId;
  }

  public void setClosedStatusId(String closedStatusId) {
    this.closedStatusId = closedStatusId;
  }

  public String getReopenedStatusId() {
    return reopenedStatusId;
  }

  public void setReopenedStatusId(String reopenedStatusId) {
    this.reopenedStatusId = reopenedStatusId;
  }

  public String getPriorityLevelId() {
    return priorityLevelId;
  }

  public void setPriorityLevelId(String priorityLevelId) {
    this.priorityLevelId = priorityLevelId;
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
            config.setClosedStatusId(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".closedStatusId"));
            config.setReopenedStatusId(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".reopenedStatusId"));
            config.setPriorityLevelId(
                (String) settings.get("com.lgtm.addon.config." + configKey + ".priorityLevelId"));
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
                "com.lgtm.addon.config." + config.getKey() + ".closedStatusId",
                config.getClosedStatusId());
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".reopenedStatusId",
                config.getReopenedStatusId());
            settings.put(
                "com.lgtm.addon.config." + config.getKey() + ".priorityLevelId",
                config.getPriorityLevelId());
            return null;
          }
        });
  }
}
