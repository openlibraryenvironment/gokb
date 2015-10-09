package com.k_int.refine.es_recon;

import io.searchbox.client.AbstractJestClient;
import io.searchbox.client.JestClient;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.MultiSearch;
import io.searchbox.core.Search;
import io.searchbox.core.SearchResult;
import io.searchbox.core.SearchResult.Hit;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation.Entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
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
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.facet.FacetBuilders;
import org.elasticsearch.search.facet.terms.TermsFacet;
import org.json.JSONException;

import com.beust.jcommander.internal.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.refine.model.Recon;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.recon.ReconJob;
import com.k_int.refine.es_recon.model.ESReconcileConfig;

@SuppressWarnings("deprecation")
public class ESReconService {
  JestHttpClient client;
  private static final int DEFAULT_PORT = 9300;
  private final List<String> indices;
  protected final Gson gson = new GsonBuilder().setDateFormat(JestHttpClient.ELASTIC_SEARCH_DATE_FORMAT).create();

  @SuppressWarnings("resource")
  public ESReconService (String host, int port, String indices) {
    
    HttpClientConfig clientConfig = new HttpClientConfig.Builder(host + ":" + port).multiThreaded(true).build();
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(clientConfig);
    client = (JestHttpClient) factory.getObject();
    this.indices = Arrays.asList(indices.split("\\,"));
  }

  public ESReconService (String host, String indices, Settings settings) {
    this(host, DEFAULT_PORT, indices);
  }

  public ESReconService (String host, String indices) {
    this(host, DEFAULT_PORT, indices);
  }

  public static Builder config () {
    return ImmutableSettings.builder(); 
  }

  public List<String> getIndexNames() {
    return indices;
  }

  private Search buildSearch (String searchSource) {
    return new Search.Builder(searchSource).addIndex(getIndexNames()).build();
  }
  
  public String[] getUniqueValues (String index, String field) throws JSONException, IOException {
    String [] vals = new String [0];
    
    // Use the original elastic search libs to create the search source.
    SearchSourceBuilder searchQuery = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .aggregation(
        AggregationBuilders.terms("types").field(field)
      )
    ;

    // Create the Jest type search.
    TermsAggregation typeAgg = client.execute(buildSearch(searchQuery.toString())).getAggregations().getTermsAggregation("types");
    
    // Now let's add each result.
    if ( typeAgg != null ) {
      List<String> valsList = new ArrayList<String> ();
      for (Entry f : typeAgg.getBuckets()) {
        valsList.add(f.getKey());
      }
      vals = valsList.toArray(vals);
    }
    
    return vals;
  }

  public MultiSearch.Builder buildMultiSearch(Search search) {
    return new MultiSearch.Builder(search);
  }

  public List<Recon> recon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, List<ReconJob> jobs) {
    // Lets Build up the queries.
    List<Recon> results = new ArrayList<Recon>();

    // Set the count to 0.
    int count = 0;
    
    // Use the first to build the multi query.
    MultiSearch.Builder multi = buildMultiSearch ( buildReconSearch((ESReconJob)jobs.get(count)) );
    while (count++ < jobs.size()) {
      multi.addSearch(buildReconSearch((ESReconJob)jobs.get(count)));
    }
   
    JestResult es_response = client.execute(multi.build());
    JsonArray responses = es_response.getJsonObject().getAsJsonArray("responses");
    
    // We should receive a responses key with all the search responses in there.
    // We should build a search response from each.
    for (int i=0; i<responses.size(); i++) {
      
      // Create the search result from the multi response.
      JsonObject obj = responses.get(i).getAsJsonObject();
      SearchResult res = new SearchResult (gson);
      res.setResponseCode(es_response.getResponseCode());
      res.setJsonString(obj.toString());
      res.setJsonObject(obj);
      res.setPathToResult(es_response.getPathToResult() + "[" + i + "]");
      
      // Matching Job.
      ESReconJob currentJob = (ESReconJob) jobs.get(i);
      
      if (res.isSucceeded()){  
        results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), res) );
      } else {
        results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), null) );
      }
    }

    return results;
  }

  public Recon createRecon (ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry) {
    Recon recon =  new Recon(judgmentHistoryEntry, "GOKb", "GOKb");
    recon.service = (String)esReconcileConfig.getService().get("url");
    return recon;
  }

  private Recon buildRecon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, String text, SearchResult res) {
    Recon recon = createRecon(esReconcileConfig, judgmentHistoryEntry);

    if (res != null) {
      HashMap<String, Object> source;
      List<?> hits = res.getHits(source.getClass());
      
      for (int i = 0; i<hits.size(); i++) {
        
        Hit<?,?> hit = (Hit<?, ?>)hits.get(i);

        String type = hit.type();
        source = (HashMap<String, Object>) hit.source;

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

  private Search buildReconSearch (ESReconJob rj) {
    return buildSearch(
      new SearchSourceBuilder().query(
          QueryBuilders.filteredQuery(
              QueryBuilders.queryString("name:" + rj.getQuery() + " OR altname:" + rj.getQuery()),
              FilterBuilders.termFilter("componentType", rj.getType())
          )
      ).toString()
    );

  }

  public void destroy () throws Exception {
    if (client != null) {
      client.shutdownClient();
    }
  }
}