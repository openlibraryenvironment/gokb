package com.k_int.refine.es_recon.model;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.util.List;
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

public class ESReconcileConfig extends ReconConfig {
  private String typeID;

  public String getTypeID () {
    return typeID;
  }

  public void setTypeID (String typeID) {
    this.typeID = typeID;
  }

  static public ReconConfig reconstruct(JSONObject obj) throws Exception {

    // Just use the Gson lib to create a new object.
    Gson gson = new Gson();
    return gson.fromJson(obj.toString(), ESReconcileConfig.class);
  }

  @Override
  public void write (JSONWriter writer, Properties options)
      throws JSONException {
    writer.value(this);
  }

  public String toJSONString() {
    Gson gson = new Gson();
    return gson.toJson(this);
  }

  @Override
  public int getBatchSize () {
    return 10;
  }

  @Override
  public String getBriefDescription (Project project, String columnName) {
    return "Reconcile cells in column " + columnName + " to type " + getTypeID();
  }

  @Override
  public ReconJob createJob (Project project, int rowIndex, Row row, String columnName, Cell cell) {
    ESReconJob job = new ESReconJob();
    try {
      job.code = jsonBuilder().startObject()
        .field("query", cell.value.toString())
        .field("type", typeID)
      .endObject().string();
    } catch (IOException e) {
      // The code will remain unset.
    }
    
    return job;
  }

  @Override
  public List<Recon> batchRecon (List<ReconJob> jobs, long historyEntryID) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Recon createNewRecon (long historyEntryID) {
    // TODO Auto-generated method stub
    return null;
  }
  static protected class ESReconJob extends ReconJob {
    String code;
    
    @Override
    public int getKey() {
        return code.hashCode();
    }
  }
}