package org.gokb.validation.types

import org.gokb.cred.RefdataValue

class ColNameMustMatchRefdataValue extends A_ValidationRule implements I_ColumnValidationRule {

  private static final String ERROR_TYPE = "incorrect_column_name"
  
  private List<String> allowed_vals
  
  private boolean valid = false

  public ColNameMustMatchRefdataValue(String columnName, String severity, String regex, String rd_cat) {

    // Call super constructor.
    super (columnName, severity)
    
    // Now need to collect allowed values for this refdata type.
    allowed_vals = RefdataValue.createCriteria().list {
      owner {
        ilike ('desc', rd_cat)
      }
      
      projections {
        property ('value')
      }
    }
    
    def match = columnName =~ regex
    if (match) {
      for (int i=0; i<allowed_vals.size() && !valid; i++) {
        String val = allowed_vals[i]
        String testVal = match[0][1]
        valid = val.equalsIgnoreCase(testVal)
      }
    }
  }

  @Override
  public boolean validate (final result, final columnDefinitions, final originalDefinitions) {
    if (!valid) {

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
      text	:"Column name ${columnName} is invalid. Allowed types for this column are \"${allowed_vals.join('\", \"')}\""
    ]
  }
}
