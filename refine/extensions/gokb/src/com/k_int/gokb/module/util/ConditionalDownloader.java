package com.k_int.gokb.module.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import com.k_int.gokb.module.GOKbModuleImpl;

public class ConditionalDownloader {

  
  /**
   * @param url The URL used to fetch the object.
   * @param etag The etag to use.
   * @return java.net.URLConnection if item has changed or conditional get not supported. Null if not changed.
   * @throws IOException
   */
  public static URLConnection getIfChanged (String url, String etag) throws IOException {
    return getIfChanged (url, null, etag);
  }
  
  /**
   * @param url The URL used to fetch the object.
   * @param since The date we are checking if changed since.
   * @return java.net.URLConnection if item has changed or conditional get not supported. Null if not changed.
   * @throws IOException
   */
  public static URLConnection getIfChanged (String url, Long since) throws IOException {
    return getIfChanged (url, since, null);
  }
  
  /**
   * @param url The URL used to fetch the object.
   * @param since The date we are checking if changed since.
   * @param etag The etag to use.
   * @return java.net.URLConnection if item has changed or conditional get not supported. Null if not changed.
   * @throws IOException
   */
  public static URLConnection getIfChanged (String url, Long since, String etag) throws IOException {

    // Get the URL.
    URL urlObj = new URL(url);

    HttpURLConnection connection = null;

    // Configure the connection properties.
    connection = (HttpURLConnection) urlObj.openConnection();
    connection.setDoOutput(true);
    connection.setConnectTimeout(GOKbModuleImpl.properties.getInt("timeout"));
    connection.setRequestProperty("Connection", "Keep-Alive");

    // Last modified date?
    if (since != null && since > 0) {
      connection.setIfModifiedSince(since);
    }

    // ETag compatibility
    if (etag != null) {
      connection.setRequestProperty("If-None-Match", etag);
    }
      
    // Connect to the service.
    connection.connect();

    // Get the response code.
    int code = connection.getResponseCode();

    switch (code) {
      case 200 :
        // Changed so returning the stream.
        return connection;
        
      case 304 :
        // Unchanged.
        return null;
        
      default :
        // Unsupported code. Let's throw an error.
        throw new IOException("Received an unsupported response code when performing conditional get. Code " + code);
    }
  }
}
