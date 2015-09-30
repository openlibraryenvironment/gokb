package com.k_int.refine.es_recon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.metadata.MappingMetaData;
import org.elasticsearch.cluster.metadata.MetaData;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.hppc.cursors.ObjectObjectCursor;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.json.JSONException;

@SuppressWarnings("deprecation")
public class ESReconService {
  TransportClient client;
  private static final int DEFAULT_PORT = 9300;
  private String[] indices;
  
  @SuppressWarnings("resource")
  public ESReconService (String host, int port, String indices, Settings settings) {
    client = new TransportClient(settings)
      .addTransportAddress(new InetSocketTransportAddress(host, port));
    this.indices = indices.split("\\,");
  }
  
  public ESReconService (String host, String indices, Settings settings) {
    this(host, DEFAULT_PORT, indices, settings);
  }
  
  public ESReconService (String host, int port, String indices) {
    this(host, port, indices, ImmutableSettings.Builder.EMPTY_SETTINGS);
  }
  
  public ESReconService (String host, String index) {
    this(host, DEFAULT_PORT, index);
  }
  
  public static Builder config () {
    return ImmutableSettings.builder(); 
  }
  
  public void addAddress(String host, int port) {
    client.addTransportAddress(new InetSocketTransportAddress(host, port));
  }
  
  public ClusterState getClusterState() {
    return client.admin().cluster().prepareState().setIndices(indices).get().getState();
  }
  
  public MetaData getMetaData() {
    return getClusterState().getMetaData();
  }
  
  public IndexMetaData getMetaData(String index) {
    return getClusterState().getMetaData().index(index);
  }
  
  public ImmutableOpenMap<String, MappingMetaData> getMappings(String index) {
    return getMetaData ( index ).getMappings();
  }
  
  public String[] getIndexNames() {
    return indices;
  }
  
  public void getAllIndexDetails() {
    for (String index : getIndexNames()) {
      for (ObjectObjectCursor<String, MappingMetaData> m : getMappings(index)) {
        String key = m.key;
        MappingMetaData val = m.value;
        System.out.println( key + " = " + val.source().toString());
      }
    }
  }
  
  public String[] getUniqueValues (String index, String field) throws JSONException, IOException {
    String [] vals = new String [0];
    if (field != null) {
      // Get the values using a facet.
      TermsFacet typeFacet = (TermsFacet) client.prepareSearch()
          .setQuery(QueryBuilders.matchAllQuery())
          .addFacet(FacetBuilders.termsFacet("types")
              .field(field)
              .allTerms(true)
              )
              .execute().actionGet().getFacets().facetsAsMap().get("types");

      if ( typeFacet != null ) {
        List<String> valsList = new ArrayList<String> ();
        for (TermsFacet.Entry f : typeFacet) {
          valsList.add(f.getTerm().string());
        }
        vals = valsList.toArray(vals);
      }
    }
    
    return vals;
  }
  
  public void destroy () throws Exception {
    if (client != null) {
      client.close();
    }
  }
}