package com.k_int.refine.es_recon.model;

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

  private String testString;

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
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public ReconJob createJob (Project project, int rowIndex, Row row, String columnName, Cell cell) {
    // TODO Auto-generated method stub
    return null;
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

  public String getTestString () {
    return testString;
  }

  public void setTestString (String testString) {
    this.testString = testString;
  }
}