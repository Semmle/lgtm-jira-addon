package com.semmle.jira.addon;

import static org.mockito.Mockito.verify;

import java.io.IOException;
import javax.servlet.http.HttpServletResponse;
import org.junit.Assert;
import org.junit.Test;

public class TestValidateRequest extends TestLgtmServletBase {

  @Test
  public void testValidateRequestForbidden() throws IOException {
    String lgtmSignature = "0caf649feee4953d87bf903ac1176c45e028df16";
    byte[] bytes = "message".getBytes();

    HttpServletResponse resp = mockResponse();

    servlet.validateRequestSignature(lgtmSignature, bytes, "wrong_secret", resp);

    verify(resp).setStatus(403);
    Assert.assertEquals(
        "{\"code\":403,\"error\":\"Forbidden.\"}", resp.getOutputStream().toString());
  }

  @Test
  public void testValidateRequestSuccess() throws IOException {
    String lgtmSignature = "0caf649feee4953d87bf903ac1176c45e028df16";
    byte[] bytes = "message".getBytes();

    HttpServletResponse resp = mockResponse();

    servlet.validateRequestSignature(lgtmSignature, bytes, "secret", resp);
  }
}
