package com.k_int.refine.es_recon;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.action.search.MultiSearchResponse;
import org.elasticsearch.action.search.MultiSearchResponse.Item;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.io.stream.InputStreamStreamInput;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.Terms;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.model.Recon;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.recon.ReconJob;
import com.k_int.refine.es_recon.model.ESReconcileConfig;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class ESReconService {
  private static final int DEFAULT_PORT = 9300;
  private final List<String> indices;
  
  Logger log = LoggerFactory.getLogger("ES-Recon Service");
  
  private final String baseUrl;
  
  public ESReconService (String host, int port, String indices) {
    this.baseUrl = host + ":" + port + "/" + indices + "/";
    this.indices = Arrays.asList(indices.split("\\,"));
  }

  public ESReconService (String host, String indices) {
    this(host, DEFAULT_PORT, indices);
  }

  public List<String> getIndexNames() {
    return indices;
  }
  
  private SearchResponse doSearch(SearchSourceBuilder search) throws UnirestException, IOException {
    final String searchBody = search.toString();
    final String url = baseUrl + "_search";
    log.debug("Posting to {} values {}", url, searchBody );
    return getSearchResponse (
      Unirest
        .post(url)
        .body(searchBody)
      .asJson().getRawBody()
    );
  }
  
  private SearchResponse getSearchResponse (InputStream is) throws IOException {
    SearchResponse sr = new SearchResponse();
    sr.readFrom(new InputStreamStreamInput ( is ));
    return sr;
  }
  
  private MultiSearchResponse getMultiSearchResponse (InputStream is) throws IOException {
    MultiSearchResponse msr = new MultiSearchResponse(null);
    msr.readFrom(new InputStreamStreamInput ( is ));
    return msr;
  }
  
  private MultiSearchResponse doMultiSearch(Collection<SearchSourceBuilder> searches) throws IOException, UnirestException {
    final String url = baseUrl + "_msearch";
    final StringBuilder searchBody = new StringBuilder();
    for (SearchSourceBuilder search : searches) {
      searchBody.append(search.toString() + "\n");
    }
    log.debug("Posting to {} values {}", url, searchBody.toString() );
    return getMultiSearchResponse(Unirest
        .post(url)
        .body(searchBody.toString())
        .asJson().getRawBody());
  }
  
  public String[] getUniqueValues (String field) throws JSONException, IOException, UnirestException {
    String [] vals = new String [0];
    
    // Use the original elastic search libs to create the search source.
    SearchSourceBuilder searchQuery = new SearchSourceBuilder()
      .query(QueryBuilders.matchAllQuery())
      .aggregation(
        AggregationBuilders.terms("types").field(field)
      )
    ;
    
    // Now we should query

    // Create the Jest type search.
    Terms types = doSearch(searchQuery).getAggregations().get("types");
    if ( types != null ) {
      List<String> valsList = new ArrayList<String> ();
      for (Terms.Bucket entry : types.getBuckets()) {
        valsList.add(entry.getKey());
      }
      vals = valsList.toArray(vals);
    }
    
    return vals;
  }

  public List<Recon> recon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, List<ReconJob> jobs) {
    // Lets Build up the queries.
    List<Recon> results = new ArrayList<Recon>();
    
    // Use the first to build the multi query.
    List<SearchSourceBuilder> searches = new ArrayList<SearchSourceBuilder> ();
    for (ReconJob job : jobs) {
      searches.add(buildReconSearch((ESReconJob)job));
    }
   
    MultiSearchResponse es_response;
    try {
      
      es_response = doMultiSearch(searches);
      
      Item[] query_results = es_response.getResponses();
      
   // Each response needs examining in turn.
      for (int i=0; i<query_results.length; i++ ) {
        
        // This is a single set of hits for a single job (row).
        Item item = query_results[i];
        
        // Matching Job.
        ESReconJob currentJob = (ESReconJob) jobs.get(i);
        if (!item.isFailure()){  
          results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), item.getResponse().getHits()) );
        } else {
          results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), null) );
        }
      }
      
      return results;
    } catch (Exception e) {
      // Just log the error and return an empty list to be safe.
      log.error("Error while reconciling.", e);
      return Collections.emptyList();
    }
  }

  public Recon createRecon (ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry) {
    Recon recon =  new Recon(judgmentHistoryEntry, "GOKb", "GOKb");
    recon.service = (String)esReconcileConfig.getService().get("url");
    return recon;
  }

  private Recon buildRecon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, String text, SearchHits searchHits) {
    Recon recon = createRecon(esReconcileConfig, judgmentHistoryEntry);
    
    final float maxScore = searchHits.getMaxScore();
    
    if (searchHits.getTotalHits() > 0) {
      SearchHit[] hits = searchHits.hits();
      for (int i = 0; i<hits.length; i++) {
        
        SearchHit hit = hits[i];
        
        String type = hit.type();
        Map<String, Object> source = hit.getSource();
        
        final float score = hit.score();
        final String name = (String) source.get("name");
        int lDistance = StringUtils.getLevenshteinDistance(StringUtils.lowerCase(text), StringUtils.lowerCase(name));
        
        // Create a recon candidate.
        ReconCandidate candidate = new ReconCandidate(
          hit.id(),
          name,
          new String[]{ type },
          compatibleScore(maxScore, score, lDistance)
        );
        
        // Add the candidate for this row.
        recon.addCandidate(candidate);
        
        if (i == 0) {
          // First object is the BEST match.
          recon.setFeature(Recon.Feature_nameMatch, text.equalsIgnoreCase(name));
          recon.setFeature(Recon.Feature_nameLevenshtein, lDistance);
  
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

  private SearchSourceBuilder buildReconSearch (ESReconJob rj) {
    return new SearchSourceBuilder()
        .query(QueryBuilders.filteredQuery(
            QueryBuilders.queryString("name:" + rj.getQuery() + " OR altname:" + rj.getQuery()),
            FilterBuilders.termFilter("componentType", rj.getType())))
      ;
  }

  public void destroy () throws Exception {
    Unirest.shutdown();
  }
}