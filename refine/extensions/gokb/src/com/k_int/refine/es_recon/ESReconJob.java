package com.k_int.refine.es_recon;

import com.google.refine.model.recon.ReconJob;

public class ESReconJob extends ReconJob {
  private String query;
  private String type;
  private int row;
  
  @Override
  public int getKey() {
    return (query + type + row).hashCode();
  }
  public String getQuery () {
    return query;
  }
  public int getRow () {
    return row;
  }
  public String getType () {
    return type;
  }
  public ESReconJob setQuery (String query) {
    this.query = query;
    return this;
  }
  public ESReconJob setRow (int row) {
    this.row = row;
    return this;
  }

  public ESReconJob setType (String type) {
    this.type = type;
    return this;
  }
}