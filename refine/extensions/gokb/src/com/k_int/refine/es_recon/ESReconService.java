package com.k_int.refine.es_recon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.MultiMatchQueryBuilder.Type;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.refine.model.Recon;
import com.google.refine.model.ReconCandidate;
import com.google.refine.model.recon.ReconJob;
import com.k_int.refine.es_recon.model.ESReconcileConfig;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

public class ESReconService {
  public static final int DEFAULT_PORT = 9200;
  private final List<String> indices;
  
  Logger log = LoggerFactory.getLogger("ES-Recon Service");
  
  private final String baseUrl;
  
  public ESReconService (String host, int port, String indices) {
    this.baseUrl = host + ":" + port + "/" + indices + "/";
    this.indices = Arrays.asList(indices.split("\\,"));
    
    // Set UniREST defaults here.
    Unirest.setTimeouts(10000, 30000);
  }

  public ESReconService (String host, String indices) {
    this(host, DEFAULT_PORT, indices);
  }

  public List<String> getIndexNames() {
    return indices;
  }
  
  private JSONObject doSearch(SearchSourceBuilder search) throws UnirestException, IOException {
    final String searchBody = search.toXContent(XContentFactory.contentBuilder(XContentType.JSON), ToXContent.EMPTY_PARAMS).string();
    final String url = baseUrl + "_search";
    JSONObject res = Unirest
      .post(url)
      .body(searchBody)
    .asJson().getBody().getObject();
    return res;
  }
  
  private JSONObject doMultiSearch(Collection<SearchSourceBuilder> searches) throws IOException, UnirestException {
    final String url = baseUrl + "_msearch";
    final StringBuilder searchBody = new StringBuilder();
    for (SearchSourceBuilder search : searches) {
      // Add an empty header to allow the search URL to control the indeces.
      searchBody.append("{}\n");
      
      // Do the conversion manually to avoid the pretty print, as the bulk actions in ES use
      // the \n character as the delimiter.
      searchBody.append(search.toXContent(XContentFactory.contentBuilder(XContentType.JSON), ToXContent.EMPTY_PARAMS).string() + "\n");
    }
    
    JSONObject res = Unirest
      .post(url)
      .body(searchBody.toString())
    .asJson().getBody().getObject();
    
    return res;
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

    // Create the Jest type search.
    JSONObject types = doSearch(searchQuery).getJSONObject("aggregations").getJSONObject("types");
    if ( types != null ) {
      List<String> valsList = new ArrayList<String> ();
      JSONArray buckets = types.getJSONArray("buckets");
      for ( int i=0; i<buckets.length();i++) {
        JSONObject entry = buckets.getJSONObject(i);
        valsList.add(entry.getString("key"));
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
   
    JSONObject es_response;
    try {
      
      es_response = doMultiSearch(searches);
      
      JSONArray query_results = es_response.getJSONArray("responses");
      
      // Each response needs examining in turn.
      for (int i=0; i<query_results.length(); i++ ) {
        
        // This is a single set of hits for a single job (row).
        JSONObject item = query_results.getJSONObject(i);
        
        // Matching Job.
        ESReconJob currentJob = (ESReconJob) jobs.get(i);
        String error = item.optString("error", null);
        if (error == null){
          results.add( buildRecon(esReconcileConfig, judgmentHistoryEntry, currentJob.getQuery(), item.getJSONObject("hits")) );
        } else {
          log.error(error);
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

  private Recon buildRecon(ESReconcileConfig esReconcileConfig, long judgmentHistoryEntry, String text, JSONObject searchHits) throws JSONException {
    Recon recon = createRecon(esReconcileConfig, judgmentHistoryEntry);
    
    final float maxScore = (float)searchHits.optDouble("max_score");
    
    if (searchHits.optInt("total") > 0) {
      JSONArray hits = searchHits.getJSONArray("hits");
      for (int i = 0; i<hits.length(); i++) {
        
        JSONObject hit = hits.getJSONObject(i);
        
        String type = hit.getString("_type");
        JSONObject source = hit.getJSONObject("_source");
        
        final float score = (float) hit.getDouble("_score");
        final String name = source.getString("name");
        
        // Add the candidate for this row.
        recon.addCandidate(buildReconCandidate(
          hit.getString("_id"),
          name,
          new String[]{ type },
          score,
          maxScore,
          text,
          source.getJSONArray("altname")
        ));
      }
    }
    
    
//    recon.match = candidate;
//    recon.matchRank = 0;
//    recon.judgment = Judgment.Matched;
//    recon.judgmentAction = "auto";
    
    // Order the recon candidates again.
    if (recon.candidates != null && recon.candidates.size() > 0) {
      Collections.sort(recon.candidates, Collections.reverseOrder(new Comparator<ReconCandidate>() {
        @Override
        public int compare (ReconCandidate o1, ReconCandidate o2) {
          if (o1 == null) {
            if (o2 == null) {
              return 0;
            }
            return 1;
          } else if (o2 == null) {
            return 1;
          }
          
          return Double.compare(o1.score, o2.score);
        }
      }));

      // Get the first item
      ReconCandidate candidate = recon.candidates.get(0);
      
      // First object is the BEST match.
      recon.setFeature(Recon.Feature_nameMatch, text.equalsIgnoreCase(candidate.name));
      recon.setFeature(Recon.Feature_nameLevenshtein, StringUtils.getLevenshteinDistance(StringUtils.lowerCase(text), StringUtils.lowerCase(candidate.name)));

      // TODO: SO - For now the type is always supplied and therefore filtered and will always be matched. 
      recon.setFeature(Recon.Feature_typeMatch, true);
    }
    
    return recon;
  }

  private SearchSourceBuilder buildReconSearch (ESReconJob rj) {
    return new SearchSourceBuilder()
        .query(QueryBuilders.filteredQuery(
            QueryBuilders.multiMatchQuery(
                rj.getQuery(), 
                "name", "altname")
                .type(Type.BEST_FIELDS),
            FilterBuilders.termFilter("componentType", rj.getType())
        ))
        .highlight(SearchSourceBuilder.highlight()
            .field("name")
            .field("altname")
        )
      ;
  }

  public void destroy () {
    try {
      Unirest.shutdown();
    } catch (IOException e) {
      log.error("Error attempting to shutdown unirest.", e);
      e.printStackTrace();
    }
  }
  
  public ReconCandidate buildReconCandidate (String id, String name, String[] types, float score, float max_score, String search_text, JSONArray alt_names) {
        
    // Now we should manipulate the data so we return the value that was matched.    
    final String text = StringUtils.lowerCase(search_text);
    
    // Find the lowest Levenshtein Distance.
    int lDistance = StringUtils.getLevenshteinDistance(text, StringUtils.lowerCase(name));
    for (int i=0; i<alt_names.length() && lDistance>0; i++) {
      
      // Grab this field value.
      final String val = alt_names.optString(i);
      
      int new_distance = StringUtils.getLevenshteinDistance(text, StringUtils.lowerCase(val));
      if ( new_distance < lDistance) {
        
        // Set this as the lowest.
        lDistance = new_distance;
        
        // Now we should set the name field to the field matched against.
        name = val;
      }
    }
    
    return new ReconCandidate(
      id,
      name,
      types,
      compatibleScore(max_score, score, lDistance)
    );
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
}