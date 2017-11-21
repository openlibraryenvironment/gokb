package org.gokb.validation.types

class HasDuplicates extends A_ValidationRule implements I_DeferredRowValidationRule {

  private static final String ERROR_TYPE = "data_invalid"

  public HasDuplicates (String columnName, String severity) {
    super(columnName, severity)

    if (!(columnName instanceof String)) {
      throw new IllegalArgumentException ("HasDuplicates rule expects a single argument of type String.")
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
      text			: "One or more rows contain duplicated data for column \"${columnName}\"",
      facetValue	: "value.match(/(\\Q${duplicates.join('\\E|\\Q')}\\E)/)",
      facetName		: "Duplicate values in ${columnName}"
    ];
  }

  @Override
  public boolean validate(final result) {

    // Check to see if the duplicates set has data.
    if (duplicates.size() > 0) {

      // Add the error.
      addError(result)
      return false
    }

    return true
  }

  private Set allVals = []
  private Set duplicates = []

  @Override
  public void process(final col_positions, final rowNum, final datarow, final reconData) {

    // Get the index for the column.
    def pos = col_positions[columnName]

    // Only check the content if the row is present in the data in the first place.
    if (pos != null) {

      // Get the value.
      def value = getRowValue(datarow, col_positions, columnName)

      // Add a value if we have one.
      if (value) {

        // Remove all whitespace at the start and end.
        value = value.trim()

        if (value != "") {

          if (allVals.contains(value)) {

            // Add to duplicates.
            duplicates << value
          } else {

            // Add to the allVals.
            allVals << value
          }
        }
      }
    }
  }
}
