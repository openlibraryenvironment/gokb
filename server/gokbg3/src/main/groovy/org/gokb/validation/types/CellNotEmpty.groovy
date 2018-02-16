package org.gokb.validation.types

class CellNotEmpty extends A_ValidationRule implements I_RowValidationRule {
  
  private static final String ERROR_TYPE = "data_invalid"
  
  public CellNotEmpty (String columnName, String severity) {
	super(columnName, severity)
	
	if (!(columnName instanceof String)) {
	  throw new IllegalArgumentException ("CellNotEmpty rule expects a single argument of type String.")
	}
  }

  @Override
  protected String getType() {
	
	// Return the type to be sent with each error message.
	return ERROR_TYPE;
  }

  @Override
  protected Map getMessageProperties() {
	
	// The extra info to be sent with each error message.
	return [
	  col			: columnName,
	  text			: "One or more rows contain no data for column \"${columnName}\"",
	  facetValue	: "if (isBlank(value), 'invalid', null)",
	  facetName		: "Invalid value in ${columnName}"
	];
  }
  
  @Override
  public boolean validate(final result, final col_positions, final rowNum, final datarow) {
	
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
		if (!value) {

		  // Flag that an error has been found in this row.
		  addError(result)
		  return false
		}
	  }
	}
	
	return !isErrorTriggered()
  }
}
