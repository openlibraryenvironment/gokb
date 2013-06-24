package org.gokb.validation.types

class CellMatches extends A_ValidationRule implements I_RowValidationRule {
  
  private static final String ERROR_TYPE = "data_invalid"

  private String regex
  
  public CellMatches (String columnName, String severity, regex) {
	super (columnName, severity)
	
	if (!(columnName instanceof String)) {
	  throw new IllegalArgumentException ("CellMatches rule requires at least a ColumnName and a Regex.")
	}
	
	this.regex = regex
  }

  @Override
  protected String getType() {
	
	// Return the type to be sent with each error message.
	return ERROR_TYPE;
  }

  @Override
  protected Map getMessageExtras() {
	
	// The extra info to be sent with each error message.
	return [
	  col			: columnName,
	  facetValue	: "and (isNonBlank(value), value.match(/${regex}/) == null)",
	  facetName		: "Invalid value in ${columnName}"
	];
  }
  
  @Override
  public void validate(final result, final col_positions, final rowNum, final datarow) {
	
	// First check should be to see if an error has already been triggered by this rule,
	// we don't want to fill the error messages with repeats.
	if (!isErrorTriggered()) {
	
	  // Get the index for the column.
	  def pos = col_positions[columnName]

	  // Only check the content if the row is present in the data in the first place.
	  if (pos != null) {

		// Get the value.
		def value = getRowValue(datarow, col_positions, columnName)

		// If blank we need to add a message.
		if (value && value != "") { 

		  // Check the regex matches the value.
		  if (!(value =~ regex)) {

    		  // Flag that an error has been found in this row.
    		  addError(result, "One or more rows do not conform to the format 'XXXX-XXXX' for the column \"${columnName}\"")
		  }
		}
	  }
	}
  }
}
