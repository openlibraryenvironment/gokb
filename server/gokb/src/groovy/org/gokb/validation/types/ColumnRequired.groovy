package org.gokb.validation.types

import org.gokb.validation.ValidationRule

class ColumnRequired implements ValidationRule {
  
  private static final String ERROR_TYPE = "missing_column"
  private String columnName
  
  public ColumnRequired (String columnName) {
	this.columnName = columnName
  }

  @Override
  public boolean valid(def project) {
	return (columnDefinitions[columnName] != null)
  }

  @Override
  public String getErrorMessage() {
	return "Import does not specify a ${columnName} column";
  }

  @Override
  public String getErrorType() {
	return ERROR_TYPE;
  }
}
