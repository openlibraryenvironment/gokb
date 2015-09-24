package com.k_int.refine.es_recon;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;

public class ESReconService {
  TransportClient client;
  private static final int DEFAULT_PORT = 9300;
  
  public ESReconService (String host, int port, Settings settings) {
    client = new TransportClient(settings)
      .addTransportAddress(new InetSocketTransportAddress(host, port));
  }
  
  public ESReconService (String host, Settings settings) {
    this(host, DEFAULT_PORT, settings);
  }
  
  public ESReconService (String host, int port) {
    this(host, port, ImmutableSettings.Builder.EMPTY_SETTINGS);
  }
  
  public ESReconService (String host) {
    this(host, DEFAULT_PORT);
  }
  
  public static Builder config () {
    return ImmutableSettings.builder(); 
  }
  
  public void addAddress(String host, int port) {
    client.addTransportAddress(new InetSocketTransportAddress(host, port));
  }
  
  public void destroy () throws Exception {
    if (client != null) {
      client.close();
    }
  }
}