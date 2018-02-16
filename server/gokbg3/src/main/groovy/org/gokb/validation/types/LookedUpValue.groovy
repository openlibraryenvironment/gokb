package org.gokb.validation.types

import org.gokb.cred.KBComponent

class LookedUpValue extends A_ValidationRule implements I_DeferredRowValidationRule {

  private static final String ERROR_TYPE = "data_invalid"
  public static final def REGEX_TEMPLATE = [".*\\:\\:\\{","\\:(\\d+)\\}\$"]
  
  public static final def ID_REGEX_TEMPLATE = ["^gokb::\\{", "\\:(\\d+)\\}\$"]

  private String regex
  private Class<? extends KBComponent> the_class
  private String id_regex

  public LookedUpValue(String columnName, String severity, Class<? extends KBComponent> the_class) {
    super(columnName, severity)
    
    regex = "${REGEX_TEMPLATE[0] + the_class.getSimpleName() + REGEX_TEMPLATE[1]}"
    
    // New ID regex
    id_regex = ID_REGEX_TEMPLATE[0] + "\\Q${the_class.getName()}\\E" + ID_REGEX_TEMPLATE[1]
    this.the_class = the_class

    if (!(columnName instanceof String)) {
      throw new IllegalArgumentException ("EnsureExistingValue rule expects a single argument of type String.")
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
      col      : columnName,
      text      : "One or more rows contains values in \"${columnName}\" that appear to not have been looked up from gokb. Please use the lookup functions on the right-click menu to populate this field",
      facetValue  : "value.match(/(\\Q${invalid_vals.join('\\E|\\Q')}\\E)/)",
      facetName    : "None looked up values in ${columnName}"
    ];
  }

  @Override
  public boolean validate(final result) {

    // Check to see if the duplicates set has data.
    if (invalid_vals.size() > 0) {

      // Add the error.
      addError(result)
      return false
    }

    return true
  }

  private Set<String> invalid_vals = []

  @Override
  public void process(final col_positions, final rowNum, final datarow, final reconData) {

    // First check should be to see if an error has already been triggered by this rule,
    // we don't want to fill the error messages with repeats.
    if (!isErrorTriggered()) {

      // Get the index for the column.
      def pos = col_positions[columnName]

      // Only check the content if the row is present in the data in the first place.
      if (pos != null) {

        // Get the value.
        def value = getRowValue(datarow, col_positions, columnName, reconData)

        if ( (value != null) && ( value.length() > 0 ) ) {

          // Default to invalid.
          boolean valid = false
          
          // We should check the id_regex first
          def match
          
          if ((match = value =~ id_regex) || (match = value =~ regex)) {

            // Matches so let's do a lookup to ensure it exists.
            try {
              long the_id = Long.parseLong(match[0][1])

              // Ensure the item actually exists.
              if (the_class.read(the_id)) {
                // All is fine.
                valid = true
              }

            } catch (Throwable t) {
              // Do nothing invalid will be returned below
            }
          }

          // Flag that data isn't valid.
          if (!valid) {
            invalid_vals << value
          }
        }
      }
    }
  }
}
