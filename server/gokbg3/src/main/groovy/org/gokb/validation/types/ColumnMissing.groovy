package org.gokb.validation.types

class ColumnMissing extends A_ValidationRule implements I_ColumnValidationRule {

  private static final String ERROR_TYPE = "missing_column"

  public ColumnMissing(String columnName, String severity) {

    // Call super constructor.
    super (columnName, severity)

    // Set the column name.
    if (!(columnName instanceof String)) {
      throw new IllegalArgumentException ("ColumRequired rule expects a single argument of type String.")
    }
  }

  @Override
  public boolean validate (final result, final columnDefinitions, final originalDefinitions) {
    if (columnDefinitions[columnName] == null) {

      // Add an error message.
      addError(result)
      return false
    }

    return true
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
      col	: columnName,
      text	:"Import does not specify an ${columnName} column"
    ]
  }
}
