package com.k_int.refine.es_recon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.search.MultiSearchRequestBuilder;
import org.elasticsearch.action.search.MultiSearchResponse.Item;
import org.elasticsearch.action.search.SearchRequestBuilder;
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
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.json.JSONException;

import com.google.refine.model.Recon;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.recon.ReconJob;
import com.k_int.refine.es_recon.model.ESReconcileConfig;

@SuppressWarnings("deprecation")
public class ESReconService {
  TransportClient client;
  private static final int DEFAULT_PORT = 9300;
  private String[] indices;
  
  private String host;
  
  @SuppressWarnings("resource")
  public ESReconService (String host, int port, String indices, Settings settings) {
    client = new TransportClient(settings)
      .addTransportAddress(new InetSocketTransportAddress(host, port));
    this.indices = indices.split("\\,");
    this.host = host;
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
        .addFacet(
          FacetBuilders.termsFacet("types")
          .field(field)
          .allTerms(true)
         ).execute().actionGet().getFacets().facetsAsMap().get("types");

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
  
  public MultiSearchRequestBuilder buildMultiSearch() {
    return client.prepareMultiSearch();
  }
  
  public List<Recon> recon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, List<ReconJob> jobs) {
    // Lets Build up the queries.
    List<Recon> results = new ArrayList<Recon>();
    
    MultiSearchRequestBuilder request = buildMultiSearch();
    for (ReconJob rj : jobs) {
      request.add(buildQueryRequest((ESReconJob) rj));
    }
    
    // Let's grab the results.
    Item[] query_results = request.execute().actionGet().getResponses();
    
    // Each response needs examining in turn.
    for (int i=0; i<query_results.length; i++ ) {
      
      // This is a single set of hits for a single job (row).
      Item item = query_results[i];
      
      // Matching Job.
      ESReconJob currentJob = (ESReconJob) jobs.get(i);
      if (!item.isFailure()){  
        results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), item.getResponse().getHits().getHits()) );
      } else {
        results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), null) );
      }
    }
    
    return results;
  }
  
  public Recon createRecon (ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry) {
    Recon recon =  new Recon(judgmentHistoryEntry, null, "http://" + host + "/resource/show/object.id");
    recon.service = (String)esReconcileConfig.getService().get("url");
    return recon;
  }
  
  private Recon buildRecon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, String text, SearchHit[] searchHits) {
    Recon recon = createRecon(esReconcileConfig, judgmentHistoryEntry);
    
    if (searchHits != null) {
      for (int i = 0; i<searchHits.length; i++) {
        
        SearchHit hit = searchHits[i];
        
        String type = hit.type();
        Map<String, Object> source = hit.getSource();
        
        // Create a recon candidate.
        ReconCandidate candidate = new ReconCandidate(
          hit.id(),
          (String) source.get("name"),
          new String[]{ type },
          hit.score()
        );
        
        // Add the candidate for this row.
        recon.addCandidate(candidate);
        
        if (i == 0) {
          // First object is the BEST match.
          recon.setFeature(Recon.Feature_nameMatch, text.equalsIgnoreCase(candidate.name));
          recon.setFeature(Recon.Feature_nameLevenshtein, StringUtils.getLevenshteinDistance(StringUtils.lowerCase(text), StringUtils.lowerCase(candidate.name)));
  
          // TODO: SO - For now the type is always supplied and therefore filtered and will always be matched. 
          recon.setFeature(Recon.Feature_typeMatch, true);
        }
      }
    }
    
    
//    recon.match = candidate;
//    recon.matchRank = 0;
//    recon.judgment = Judgment.Matched;
//    recon.judgmentAction = "auto";
    
    return recon;
  }
   
  private SearchRequestBuilder buildQueryRequest (ESReconJob rj) {
    
    return client.prepareSearch(indices)
      .setQuery(QueryBuilders.filteredQuery(
          QueryBuilders.queryString("name:" + rj.getQuery() + " OR altname:" + rj.getQuery()),
          FilterBuilders.termFilter("componentType", rj.getType())))
    ;
  }
  
  public void destroy () throws Exception {
    if (client != null) {
      client.close();
    }
  }
}