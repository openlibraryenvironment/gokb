package org.gokb.validation.types

class ColumnRequired extends A_ValidationRule implements I_ColumnValidationRule {
  
  private static final String ERROR_TYPE = "missing_column"
  
  private String columnName
  
  public ColumnRequired (String columnName) {
	if (!(columnName instanceof String)) {
	  throw new IllegalArgumentException ("ColumRequired rule expects a single argument of type String.")
	}
	this.columnName = columnName
  }

  @Override
  public void validate (final result, final columnDefinitions) {
	if (columnDefinitions[columnName] == null) {
	  
	  // Add an error message.
	  addError(result, "Import does not specify an ${columnName} column")
	}
  }

  @Override
  public int getSeverity() {
	// Returnt he severity to be sent with eah error message.
	return SEVERITY_ERROR
  }

  @Override
  protected String getType() {
	// Return the type to be sent with each error message.
	return ERROR_TYPE;
  }

  @Override
  protected Map getMessageExtras() {
	// The extra info to be sent with each error message.
	return [col: columnName];
  }
}
