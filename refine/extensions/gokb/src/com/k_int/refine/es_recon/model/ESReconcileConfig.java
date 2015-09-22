package com.k_int.refine.es_recon.model;

import java.util.List;

import com.google.refine.model.recon.StandardReconConfig;

public class ESReconcileConfig extends StandardReconConfig {

  public ESReconcileConfig (String service, String identifierSpace, String schemaSpace, String typeID, String typeName, boolean autoMatch, List<ColumnDetail> columnDetails) {
    super(service, identifierSpace, schemaSpace, typeID, typeName, autoMatch, columnDetails);
  }
}