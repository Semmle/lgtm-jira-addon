package com.semmle.jira.addon;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonSerialize.Inclusion;

public class Util {

  public static final ObjectMapper JSON = new ObjectMapper();

  static {
    JSON.configure(JsonParser.Feature.AUTO_CLOSE_SOURCE, false);
    JSON.configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
    JSON.setSerializationInclusion(Inclusion.NON_NULL);
  }

  public static boolean signatureIsValid(
      String lgtmSecret, byte[] requestBytes, String lgtmSignature) {
    String hmac = calculateHmac(lgtmSecret, requestBytes);
    return (hmac != null) ? hmac.equals(lgtmSignature) : false;
  }

  public static String calculateHmac(String lgtmSecret, byte[] requestBytes) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(lgtmSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
      return toHex(mac.doFinal(requestBytes));
    } catch (NoSuchAlgorithmException | InvalidKeyException | IllegalArgumentException e) {
      return null;
    }
  }

  private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
  /** Convert an array of bytes into an array of lower-case hex digits. */
  public static String toHex(byte... bytes) {
    StringBuilder strBldr = new StringBuilder(bytes.length * 2);
    char[] hexchars = HEX_CHARS;
    for (byte b : bytes) {
      strBldr.append(hexchars[(b >>> 4) & 0xF]).append(hexchars[b & 0xF]);
    }
    return strBldr.toString();
  }

  public static String escape(String str) {
    return escapeMarkdown(escapeHTML(str));
  }

  private static String escapeMarkdown(String str) {

    List<Character> specialMarkdownChars =
        Arrays.asList('_', '*', '(', ')', '[', ']', '#', '+', '-', '!', '^', '~', '|');

    if (str == null) return null;
    StringBuilder res = new StringBuilder();

    boolean escapeOctothorp = true;
    for (int i = 0, n = str.length(); i < n; ++i) {
      char c = str.charAt(i);
      if (specialMarkdownChars.contains(c) && (c != '#' || escapeOctothorp))
        res.append("\\").append(c);
      else res.append(c);

      // If this character is an '&' and the next character is an '#' it will not be escaped.
      // This is to avoid escaping it in HTML entities, e.g. &#x2603;.
      escapeOctothorp = (c != '&');
    }
    return res.toString();
  }

  private static String escapeHTML(String s) {
    if (s == null) return null;

    int length = s.length();
    // initialize a StringBuilder with initial capacity of twice the size of the string,
    // except when its size is zero, or when doubling the size causes overflow
    StringBuilder sb = new StringBuilder(length * 2 > 0 ? length * 2 : length);
    for (int i = 0; i < length; i++) {
      char c = s.charAt(i);
      switch (c) {
        case '<':
          sb.append("&lt;");
          break;
        case '>':
          sb.append("&gt;");
          break;
        case '&':
          sb.append("&amp;");
          break;
        case '"':
          sb.append("&quot;");
          break;
        case '\'':
          sb.append("&#39;");
          break;
        default:
          sb.append(c);
          break;
      }
    }
    return sb.toString();
  }
}
