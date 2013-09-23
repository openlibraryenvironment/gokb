package org.gokb.validation.types

class CellMatches extends A_ValidationRule implements I_RowValidationRule {

  private static final String ERROR_TYPE = "data_invalid"

  protected String regex
  protected String text
  protected String facetValue

  public CellMatches (String columnName, String severity, String regex, String text, String facetValue) {
    super (columnName, severity)

    if (!(columnName instanceof String)) {
      throw new IllegalArgumentException ("CellMatches rule requires at least a ColumnName and a Regex.")
    }

    this.regex = regex
    this.text = text
    this.facetValue = facetValue
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
      text			: (text),
      facetValue	: (facetValue),
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
        if (value != null && value != "") {

          // Check the regex matches the value.
          if (!(value ==~ regex)) {

            // Flag that an error has been found in this row.
            addError(result)
            return false
          }
        }
      }
    }

    return !isErrorTriggered()
  }
}
