package org.gokb.validation.types

import org.gokb.cred.RefdataValue

class IsOneOfRefdata extends CellMatches {

  private static final String ERROR_TYPE = "data_invalid"

  public IsOneOfRefdata (String columnName, String severity, String rd_cat, boolean case_sensitive = false) {

    // Pass nulls to the constructor as we are going to set them afterwards.
    super (columnName, severity, null, null, null)

    // Now need to collect allowed values for this refdata type.
    Set<String> all_items = RefdataValue.createCriteria().list {
      and {
        owner {
          ilike ('desc', rd_cat)
        }
        
        isNull('useInstead')
      }

      projections {
        property ('value')
      }
    } as Set

    this.regex = (case_sensitive ? "" : "(?i)") + "^(\\Q${all_items.join('\\E|\\Q')}\\E)\$"
    this.text = "One or more rows contain invalid values in the column \"${columnName}\"." +
        "Allowed values are \"${all_items.join('\", \"')}\". Values are ${(case_sensitive ? '' : 'not ')}case sensitive."
    this.facetValue = "if (and (isNonBlank(value), isNull(value.match(/${regex}/))), 'invalid', null)"
  }
}