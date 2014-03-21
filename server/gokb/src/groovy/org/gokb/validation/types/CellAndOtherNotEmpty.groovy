package org.gokb.validation.types

class CellAndOtherNotEmpty extends A_ValidationRule implements I_RowValidationRule {

  private static final String ERROR_TYPE = "data_invalid"

  private String otherColumn

  public CellAndOtherNotEmpty (String columnName, String severity, String otherColumn) {
    super(columnName, severity)

    // Set the other column.
    this.otherColumn = otherColumn
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
      text			: "One or more rows contain no data in both column \"${columnName}\" and \"${otherColumn}\"",
      facetValue	: "if (and(isBlank(value), isBlank(cells[gokbCaseInsensitiveCellLookup('${otherColumn}')].value)), 'invalid', null)",
      facetName		: "Both ${columnName} and ${otherColumn} blank."
    ];
  }

  @Override
  public boolean validate(final result, final col_positions, final rowNum, final datarow) {

    // First check should be to see if an error has already been triggered by this rule,
    // we don't want to fill the error messages with repeats.
    if (!isErrorTriggered()) {

      // Get the index for the column.
      def this_pos = col_positions[columnName]
      def other_pos = col_positions[otherColumn]

      // Only check the content if the row is present in the data in the first place.
      if (this_pos != null && other_pos != null) {

        // Get the value.
        String val1 = getRowValue(datarow, col_positions, columnName)
        String val2 = getRowValue(datarow, col_positions, otherColumn)
        String value = "${(val1 ?: '')}${val2 ?: ''}"

        // If blank we need to add a message.
        value = value.trim()
        if ("".equals(value)) {

          // Flag that an error has been found in this row.
          addError(result)
          return false
        }
      }
    }

    return !isErrorTriggered()
  }
}
