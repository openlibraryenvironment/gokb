package com.k_int.refine.es_recon;

import io.searchbox.client.JestClientFactory;
import io.searchbox.client.JestResult;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.core.MultiSearch;
import io.searchbox.core.Search;
import io.searchbox.core.search.aggregation.TermsAggregation;
import io.searchbox.core.search.aggregation.TermsAggregation.Entry;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.settings.ImmutableSettings.Builder;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.refine.model.Recon;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.recon.ReconJob;
import com.k_int.refine.es_recon.model.ESReconcileConfig;

public class ESReconService {
  JestHttpClient client;
  private static final int DEFAULT_PORT = 9300;
  private final List<String> indices;
  protected final Gson gson = new GsonBuilder().setDateFormat(JestHttpClient.ELASTIC_SEARCH_DATE_FORMAT).create();
  Logger log = LoggerFactory.getLogger("ES-Recon Service");

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
   
    JestResult es_response;
    try {
      es_response = client.execute(multi.build());
      JsonArray responses = es_response.getJsonObject().getAsJsonArray("responses");
      
      // We should receive a responses key with all the search responses in there.
      // We should build a search response from each.
      for (int i=0; i<responses.size(); i++) {
        
        // Create the search result from the multi response.
        JsonObject obj = responses.get(i).getAsJsonObject();
        
        // Matching Job.
        ESReconJob currentJob = (ESReconJob) jobs.get(i);
        
        if (!obj.get("error").getAsBoolean()){  
          results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), obj ) );
        } else {
          results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), null ) );
        }
      }
    } catch (IOException e) {
      // Just log the error and return an empty list to be safe.
      log.error("Error while reconciling.", e);
      return Collections.emptyList();
    }

    return results;
  }

  public Recon createRecon (ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry) {
    Recon recon =  new Recon(judgmentHistoryEntry, "GOKb", "GOKb");
    recon.service = (String)esReconcileConfig.getService().get("url");
    return recon;
  }

  private Recon buildRecon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, String text, JsonObject res) {
    Recon recon = createRecon(esReconcileConfig, judgmentHistoryEntry);

    if (res != null) {

      JsonArray hits = res.getAsJsonObject("hits").getAsJsonArray("hits");
      
      // Grab the max score here.
      final float max_score = res.getAsJsonObject("hits").get("max_score").getAsFloat();
      
      for (int i = 0; i<hits.size(); i++) {
        
        JsonObject hit = hits.get(i).getAsJsonObject();

        String type = hit.get("_type").getAsString();
        JsonObject source = hit.getAsJsonObject("_source");
        
        // The resource name.
        String name = source.get("name").getAsString();
        
        // The Levenshtein distance.
        int l_distance = StringUtils.getLevenshteinDistance(StringUtils.lowerCase(text), StringUtils.lowerCase(name));

        // Create a recon candidate.
        ReconCandidate candidate = new ReconCandidate(
            hit.get("_id").getAsString(),
            name,
            new String[]{ type },
            compatibleScore (max_score, hit.get("_score").getAsFloat(), l_distance)
        );

        // Add the candidate for this row.
        recon.addCandidate(candidate);

        if (i == 0) {
          // First object is the BEST match.
          recon.setFeature(Recon.Feature_nameMatch, text.equalsIgnoreCase(candidate.name));
          recon.setFeature(Recon.Feature_nameLevenshtein, l_distance);

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
  
  private float compatibleScore (float es_max_score, float es_doc_score, int levenshtein_distance) {
        
    // Default to the doc score over the max.
    float new_score = (es_doc_score/es_max_score);
    
    // We don't want to treat strings that differ in any way as 100% certain matches.
    // So we use the distance measure to offset the results slightly 
    if (levenshtein_distance > 0) {
      
      // Cap it at 99 to allow a very small score.
      if (levenshtein_distance > 99) {
        levenshtein_distance = 99;
      }
      
      // Apply a small offset to the results.
      new_score = new_score - (float)(levenshtein_distance / 100f);
    }
    
    // The new elastic search score
    return (new_score * 100f);
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