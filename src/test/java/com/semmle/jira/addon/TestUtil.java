package com.semmle.jira.addon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TestUtil {
  @Test
  public void testCalculateHmac() {
    String lgtmSecret = "secret";
    byte[] requestBytes = "request".getBytes();
    String hmac = Util.calculateHmac(lgtmSecret, requestBytes);

    String expected = "b5519de5033e5c058109efea0d2f0a3ed77626e8";
    assertEquals(expected, hmac);
  }

  @Test
  public void testCalculateHmacEmptySecret() {
    String lgtmSecret = "";
    byte[] requestBytes = "request".getBytes();
    String hmac = Util.calculateHmac(lgtmSecret, requestBytes);

    String expected = null;
    assertEquals(expected, hmac);
  }

  @Test
  public void testCalculateHmacEmptyBytes() {
    String lgtmSecret = "secret";
    byte[] requestBytes = "".getBytes();
    String hmac = Util.calculateHmac(lgtmSecret, requestBytes);

    String expected = "25af6174a0fcecc4d346680a72b7ce644b9a88e8";
    assertEquals(expected, hmac);
  }

  @Test
  public void testSignatureIsValidEmptySecret() {
    String lgtmSecret = "";
    byte[] requestBytes = "request".getBytes();
    String lgtmSignature = "25af6174a0fcecc4d346680a72b7ce644b9a88e8";

    boolean expected = false;
    assertEquals(expected, Util.signatureIsValid(lgtmSecret, requestBytes, lgtmSignature));
  }

  @Test
  public void testSignatureIsValidSuccess() {
    String lgtmSecret = "secret";
    byte[] requestBytes = "request".getBytes();
    String lgtmSignature = "b5519de5033e5c058109efea0d2f0a3ed77626e8";

    assertTrue(Util.signatureIsValid(lgtmSecret, requestBytes, lgtmSignature));
  }

  @Test
  public void testSignatureIsValidInvalidSignature() {
    String lgtmSecret = "secret";
    byte[] requestBytes = "request".getBytes();
    String lgtmSignature = "25af6174a0fcecc4d346680a72b7ce644b9a88e8";

    assertFalse(Util.signatureIsValid(lgtmSecret, requestBytes, lgtmSignature));
  }
}
