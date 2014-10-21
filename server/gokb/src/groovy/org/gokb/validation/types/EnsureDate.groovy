package org.gokb.validation.types

import org.gokb.cred.KBComponent

class EnsureDate extends A_ValidationRule implements I_RowValidationRule {

  private static final String ERROR_TYPE = "date_invalid"
  private String transformation;

  public EnsureDate(String columnName, String severity, String transformation = "value.toDate()" ) {
    super(columnName, severity)

    if (!(columnName instanceof String)) {
      throw new IllegalArgumentException ("EnsureDate rule expects a single argument of type String.")
    }
    
    this.transformation = transformation;
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
      col			       : columnName,
      text			     : "One or more rows contains invalid dates in the column \"${columnName}\".",
      facetValue	   : "if (isNonBlank(value), if (value.toDate().toString() != value.toString(), 'invalid', null), null)",
      facetName		   : "Invalid dates in ${columnName}",
      transformation : "${transformation}"
    ]
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

        // Get the raw value.
        def definition = datarow.cells[col_positions[columnName]]

        if (definition) {
          
          
          def value = definition.v ? (definition.v as String).trim() : null

          if (value != null && value != "" && (definition.t == null || definition.t != 'date')) {
            // Invalid date value...
            addError(result)
          }
        }
      }
    }

    return !isErrorTriggered()
  }
}
