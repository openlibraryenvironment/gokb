package org.gokb.validation.types

class IsOneOf extends CellMatches {

  private static final String ERROR_TYPE = "data_invalid"

  public IsOneOf (String columnName, String severity, Collection<String> items, boolean case_sensitive = false) {

    // Pass nulls to the constructor as we are going to set them afterwards.
    super (columnName, severity, null, null, null)

    // Make a set
    Set<String> all_items = items as Set

    this.regex = (case_sensitive ? "" : "(?i)") + "^(\\Q${all_items.join('\\E|\\Q')}\\E)\$"
    this.text = "One or more rows contain invalid values in the column \"${columnName}\"." +
        "Allowed values are \"${all_items.join('\", \"')}\". Values are ${(case_sensitive ? '' : 'not ')}case sensitive."
    this.facetValue = "if (and (isNonBlank(value), isNull(value.match(/${regex}/))), 'invalid', null)"
  }
}