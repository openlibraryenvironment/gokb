package org.gokb.validation.types

import java.util.Map


class CellNotEmpty extends A_ValidationRule implements I_RowValidationRule {
  
  private static final String ERROR_TYPE = "data_invalid"
  
  private String columnName
  
  public CellNotEmpty (String columnName) {
	if (!(columnName instanceof String)) {
	  throw new IllegalArgumentException ("CellNotEmpty rule expects a single argument of type String.")
	}
	this.columnName = columnName
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
	return [col: columnName, facet: ""];
  }
  
  @Override
  public void validate(final result, final col_positions, final rowNum, final datarow) {
	
	// First check should be to see if an error has already been triggered by this rule,
	// we don't want to fill the error messages with repeats.
	if (!isErrorTriggered()) {
	
	  // Get the index for the column.
	  def pos = col_positions[columnName]

	  // Only check the content if the row is present in the data in the first place.
	  if (pos) {

		// Get the value.
		def value = getRowValue(datarow, col_positions, columnName)

		// If blank we need to add a message.
		if (!value) {

		  // Flag that an error has been found in this row.
		  addError(result, "One or more rows contain no data for column \"${columnName}\"")
		}
	  }
	}
  }
}
