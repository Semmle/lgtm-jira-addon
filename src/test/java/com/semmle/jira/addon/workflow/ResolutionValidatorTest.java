package com.semmle.jira.addon.workflow;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.atlassian.jira.config.ResolutionManager;
import com.atlassian.jira.issue.MutableIssue;
import com.atlassian.jira.issue.resolution.Resolution;
import com.atlassian.jira.junit.rules.AvailableInContainer;
import com.atlassian.jira.junit.rules.MockitoMocksInContainer;
import com.opensymphony.workflow.InvalidInputException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.mockito.Mock;

public class ResolutionValidatorTest {

  @Rule public RuleChain mocksInContainer = MockitoMocksInContainer.forTest(this);

  @Mock @AvailableInContainer private ResolutionManager mockResolutionManager;

  protected ResolutionValidator validator;
  protected MutableIssue issue;

  private List<Resolution> resolutions = new ArrayList<>();

  @Before
  public void setup() {
    resolutions.add(mockResolution("done", "Done"));
    resolutions.add(mockResolution("wontdo", "Won't Do"));
    issue = mock(MutableIssue.class);
    validator =
        new ResolutionValidator() {
          @Override
          protected MutableIssue getIssue(@SuppressWarnings("rawtypes") Map map) {
            return issue;
          }
        };
    when(mockResolutionManager.getResolutions()).thenReturn(resolutions);
    for (Resolution resolution : resolutions) {
      when(mockResolutionManager.getResolution(resolution.getId())).thenReturn(resolution);
    }
  }

  private static Resolution mockResolution(String id, String name) {
    Resolution result = mock(Resolution.class);
    when(result.getId()).thenReturn(id);
    when(result.getName()).thenReturn(name);
    return result;
  }

  @Test
  public void testValidates() throws InvalidInputException {
    testValidateResolution("done", Operator.EQUALS, "done", true);
    testValidateResolution("done", Operator.NOT_EQUALS, "wontdo", true);
    testValidateResolution("done", Operator.EQUALS, "wontdo", false);
    testValidateResolution("done", Operator.NOT_EQUALS, "done", false);
    testValidateResolution("invalid", Operator.EQUALS, "wontdo", false);
    // The function merely checks that the resolution IDs match, it does not validate that the
    // resolutions actually exist.
    testValidateResolution("invalid", Operator.EQUALS, "invalid", true);
  }

  public void testValidateResolution(
      String resolution, Operator op, String ticketResolution, boolean expected)
      throws InvalidInputException {
    Map<String, String> args = new LinkedHashMap<>();
    args.put(ResolutionValidator.FIELD_RESOLUTION, resolution);
    args.put(ResolutionValidator.FIELD_OPERATOR, op.name());
    when(issue.getResolutionId()).thenReturn(ticketResolution);
    try {
      validator.validate(Collections.emptyMap(), args, null);
    } catch (InvalidInputException e) {
      if (expected) throw e;
      return;
    }
    if (!expected) Assert.fail("Validate should have failed, but it didn't.");
  }
}
