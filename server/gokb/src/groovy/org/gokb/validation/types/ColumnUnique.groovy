package org.gokb.validation.types

class ColumnUnique extends A_ValidationRule implements I_ColumnValidationRule {

  private static final String ERROR_TYPE = "unique_column"

  public ColumnUnique(String columnName, String severity) {

    // Call super constructor.
    super (columnName, severity)

    // Set the column name.
    if (!(columnName instanceof String)) {
      throw new IllegalArgumentException ("ColumnUnique rule expects a single argument of type String.")
    }
  }

  @Override
  public boolean validate (final result, final columnDefinitions) {
    
    // Create a set to house the column names.
    Set<String> colnames = []
    
    for (String key_name : columnDefinitions.keySet()) {
      
      // Case-insensitively match.
      String ikey_name = (key_name ?: "").toLowerCase()
      
      if (colnames.contains(ikey_name)) {
        
        // Add an error message.
        addError(result)
        return false;
      }
      
      // Add to the set.
      colnames << ikey_name
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
      text	:"There is more than one column defined with the title '${columnName}'. Please note that column names are case-insesitive in GOKb."
    ]
  }
}
