package org.gokb.validation.types

class IsOneOf extends CellMatches {
  
  private static final String ERROR_TYPE = "data_invalid"
  
  public IsOneOf (String columnName, String severity, Set<String> items, boolean case_sensitive = false) {
	super (columnName, severity)
	
	this.regex = (case_sensitive ? "" : "(?i)") + "^(\\Q${items.join('\\E|\\Q')}\\E)\$"
	this.text = "One or more rows contain invalid values in the column \"${columnName}\"." +
		"Allowed values are \"${items.join('\", \"')}\". Values are ${(case_sensitive ? '' : 'not ')}case sensitive."
	this.facetValue = "and (isNonBlank(value), value.match(/${regex}/))"
  }
}