package com.k_int.refine.es_recon.model;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;

import com.google.gson.Gson;
import com.google.refine.model.Cell;
import com.google.refine.model.Project;
import com.google.refine.model.Recon;
import com.google.refine.model.Row;
import com.google.refine.model.recon.ReconConfig;
import com.google.refine.model.recon.ReconJob;
import com.k_int.gokb.module.GOKbModuleImpl;
import com.k_int.refine.es_recon.ESReconJob;
import com.k_int.refine.es_recon.ESReconService;

public class ESReconcileConfig extends ReconConfig {
  static public ReconConfig reconstruct(JSONObject obj) throws Exception {
    
    String json = obj.toString();
    
    // Just use GSON.
    Gson gson = new Gson();

    // Just use the Gson lib to create a new object.
    return gson.fromJson(json, ESReconcileConfig.class);
  }
  
  private String type;
  private Map<String, Object> service;
  public static final String MODE_KEY = "ElasticSearch";

  @Override
  public List<Recon> batchRecon (List<ReconJob> jobs, long historyEntryID) {    
    // Lets build up a multi query
    ESReconService recon = GOKbModuleImpl.singleton.getReconService();
    return recon.recon(this, historyEntryID, jobs);
  }

  @Override
  public ReconJob createJob (Project project, int rowIndex, Row row, String columnName, Cell cell) {
    ESReconJob job = new ESReconJob();
    job
      .setQuery(cell.value.toString())
      .setType(type)
      .setRow(rowIndex)
    ;
    return job;
  }

  @Override
  public Recon createNewRecon (long historyEntryID) {
    return GOKbModuleImpl.singleton.getReconService().createRecon(this, historyEntryID);
  }

  @Override
  public int getBatchSize () {
    return 10;
  }

  @Override
  public String getBriefDescription (Project project, String columnName) {
    return "Reconcile cells in column " + columnName + " to type " + getType() + " using " + service.get("name");
  }

  public Map<String, Object> getService () {
    return service;
  }

  public String getType () {
    return type;
  }

  public void setService (Map<String, Object> service) {
    this.service = service;
  }

  public void setType (String type) {
    this.type = type;
  }

  @Override
  public void write (JSONWriter writer, Properties options)
      throws JSONException {
    Gson gson = new Gson();
    
    writer.object()
      .key("mode").value(GOKbModuleImpl.singleton.getName() + "/" + MODE_KEY)
      .key("type").value(type)
      .key("service").value(new JSONObject(gson.toJson(service)))
    .endObject();
  }
}