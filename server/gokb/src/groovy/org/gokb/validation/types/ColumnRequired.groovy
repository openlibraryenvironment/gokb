package org.gokb.validation.types

class ColumnRequired implements I_ColumnValidationRule {
  
  private static final String ERROR_TYPE = "missing_column"
  
  private String columnName
  
  private ColumnRequired (String columnName) {
	this.columnName = columnName
  }
  
  @Override
  public ColumnRequired getInstance (columnName) {
	return new ColumnRequired (columnName)
  }

  @Override
  public boolean validate (final result, final columnDefinitions) {
	if (columnDefinitions[columnName] != null) {
	  // Add the message and set to false.
	  result.status = false
	  result.messages.add([text:"Import does not specify an ${columnName} column", type:"${ERROR_TYPE}", col: "${columnName}"]);
	}
  }

  @Override
  public int getSeverity() {
	return I_ValidationRule.SEVERITY_ERROR
  }
}
