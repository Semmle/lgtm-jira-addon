package com.semmle.jira.addon;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import java.util.LinkedHashMap;
import java.util.Map;

public class MockPluginSettings implements PluginSettings {

  private final Map<String, Object> settings = new LinkedHashMap<>();

  @Override
  public Object get(String key) {
    return settings.get(key);
  }

  @Override
  public Object put(String key, Object value) {
    return settings.put(key, value);
  }

  @Override
  public Object remove(String key) {
    return settings.remove(key);
  }
}
