package org.gokb.validation.types

import org.gokb.GOKbTextUtils;
import org.gokb.cred.KBComponent

class IsSimilar extends A_ValidationRule implements I_DeferredRowValidationRule {

  private static final String ERROR_TYPE = "data_warning"
  private final Class<? extends KBComponent> type_class
  private final double threshold

  public IsSimilar(String columnName, String severity, Class<? extends KBComponent> clazz, double threshold) {
    super(columnName, severity)

    // The type.
    type_class = clazz
    this.threshold = threshold
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
      text			: "\"${currentValue}\" in column \"${columnName}\" was detected to be similar to an existing value of \"${currentSimilarity}\"",
      facetValue	: "value.match(/(\\Q${currentValue}\\E)/)",
      facetName		: "Similar values to \"${currentSimilarity}\""
    ]
  }

  @Override
  public boolean validate(final result) {

    // Map of similarities.
    HashMap<String, String> similarities = [:]

    // Check the unique values.
    allVals.each { String val ->

      double maxValue = 0

      List<? extends KBComponent> matching_components = type_class.list().each { KBComponent comp ->

        // Compare the normalised value of this val with the data.
        String norm_val = GOKbTextUtils.generateKey(val)

        double sim = GOKbTextUtils.cosineSimilarity(norm_val, comp.normname)
        if (sim >= threshold && sim > maxValue && sim < 1) {

          // We have determined that the current is a near match.
          similarities[val] = comp.name
        }
      }
    }

    // Go through each similarity.
    boolean valid = true
    similarities.each { String value, String similar_to ->

      currentValue = value
      currentSimilarity = similar_to

      // Add an error.
      addError(result)

      valid = false
    }

    return valid
  }

  private String currentValue
  private String currentSimilarity

  private Set allVals = []

  @Override
  public void process(final col_positions, final rowNum, final datarow, final reconData) {

    // Get the index for the column.
    def pos = col_positions[columnName]

    // Only check the content if the column is present in the data in the first place.
    if (pos != null) {

      // Get the value.
      String value = getRowValue(datarow, col_positions, columnName)

      // Add a value if we have one.
      if (value) {

        // Remove all whitespace at the start and end.
        value = value.trim()

        if (value != "") {

          allVals << value
        }
      }
    }
  }
}
