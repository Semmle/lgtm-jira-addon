package com.semmle.jira.addon;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.semmle.jira.addon.LgtmServlet.InvalidRequestException;
import com.semmle.jira.addon.util.Util;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.AccessControlException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.junit.Before;
import org.junit.Test;

public class TestValidateRequest extends TestLgtmServletBase {
  HttpServletRequest req = mock(HttpServletRequest.class);

  @Before
  public void setup() throws JsonGenerationException, JsonMappingException, IOException {
    Request request = TestCreateIssue.createRequest("test", "Query", "test.cpp", "Security Error");

    InputStream byteArrayInputStream =
        new ByteArrayInputStream(Util.JSON.writeValueAsBytes(request));
    ServletInputStream servletInputStream =
        new ServletInputStream() {
          public int read() throws IOException {
            return byteArrayInputStream.read();
          }
        };
    when(req.getInputStream()).thenReturn(servletInputStream);
    when(req.getHeader("x-lgtm-signature")).thenReturn("ae3e7b2a5ce16d5a09d47e8155ae9adbcb60e263");
  }

  @Test(expected = AccessControlException.class)
  public void testValidateRequestForbidden()
      throws IOException, AccessControlException, InvalidRequestException {
    servlet.validateRequest(req, "wrong_secret");
  }

  @Test
  public void testValidateRequestSuccess()
      throws IOException, AccessControlException, InvalidRequestException {
    servlet.validateRequest(req, "secret");
  }
}
