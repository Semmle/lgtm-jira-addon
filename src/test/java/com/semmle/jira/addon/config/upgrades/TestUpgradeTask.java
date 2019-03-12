package com.semmle.jira.addon.config.upgrades;

import static org.junit.Assert.assertTrue;

import java.util.Set;
import java.util.regex.Pattern;
import org.junit.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.RegexPatternTypeFilter;

public class TestUpgradeTask {
  @Test
  public void checkHighestBuildNumber()
      throws ClassNotFoundException, IllegalArgumentException, IllegalAccessException,
          NoSuchFieldException, SecurityException {
    final ClassPathScanningCandidateComponentProvider provider =
        new ClassPathScanningCandidateComponentProvider(false);
    provider.addIncludeFilter(
        new RegexPatternTypeFilter(
            Pattern.compile("com.semmle.jira.addon.config.upgrades.UpgradeTask[0-9]*")));
    final Set<BeanDefinition> classes =
        provider.findCandidateComponents("com.semmle.jira.addon.config.upgrades");

    int maxBuildNumber = 0;
    for (BeanDefinition bean : classes) {
      Class<?> upgradeTaskClass = Class.forName(bean.getBeanClassName());
      int currentBuildNumber = upgradeTaskClass.getField("buildNumber").getInt(null);
      if (currentBuildNumber > maxBuildNumber) {
        maxBuildNumber = currentBuildNumber;
      }
    }

    Class<?> upgradeTaskClass = Class.forName("com.semmle.jira.addon.config.upgrades.UpgradeTask");
    int highestBuildNumber = upgradeTaskClass.getField("highestBuildNumber").getInt(null);

    assertTrue(
        "The value of highestBuildNumber in UpgradeTask must match the highest of all the build numbers of its subclasses",
        highestBuildNumber == maxBuildNumber);
  }
}
