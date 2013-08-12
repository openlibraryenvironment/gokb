package org.gokb.validation.types

abstract class A_ValidationRule {
  
  public static final String SEVERITY_ERROR = "error"
  public static final String SEVERITY_WARNING = "warning"
  
  private boolean errorTriggered = false
  String columnName
  String severity
  
  private A_ValidationRule () { /* Hide the default constructor */ }
  
  protected A_ValidationRule (String columnName, String severity) {
	this.severity = severity
	this.columnName = columnName
  }
  
  protected boolean isErrorTriggered() {
	errorTriggered
  }
  
  protected void flagErrorTriggered() {
	errorTriggered = true
  }
  
  /**
   * Return the type to be sent with error messages.
   */
  protected abstract String getType()
  
  /**
   * Return the extra information to be sent with error messages.
   */
  protected abstract Map getMessageProperties ()
  
  /**
   * Add an error message.
   */
  protected void addError(final result) {
	
	// Get the extras added by this rule.
	Map message = ['severity' : getSeverity(), 'type' : getType()] + getMessageProperties()
	
	// If messages isn't set then default to empty list.
	if (result.messages == null) {
	  result.messages = []
	}
	
	// Set the status and add the message.
	result.status = false
	result.messages.add(message)
	
	// Set the error triggered flag.
	flagErrorTriggered()
  }
  
  protected List<String> getRowValue(datarow, col_positions, colname) {
	
	String result = null
	if ( col_positions[colname] != null ) {
	  result << jsonv(datarow.cells[col_positions[colname]])
	}
	result
  }
  
  private Map<String, List<String>> col_names = [:]
  private List<String> doRegexMatchOnColumns(Map col_positions, String colname) {
	
	List<String> val = col_names [colname]
	
	// Return now if present
	if (val != null) return val
	
	// Initiate.
	val = []
	
	// Check for asterisk.
	if (colname.contains("*")) {
	  
	  // We need to escape the dots and replace the asterisk.
	  String regex = colname.replace(".", "\\\\.").replace("*", "[^\\\\.]")
	  
	  // Now we have the col_name as a regex we can check to see if any of the colnames match it.
	  col_positions.keySet().each {
		if (regex ==~ it) {
		  // Add to the list.
		  val << it.toString()
		}
	  }
	} else {
		// Just need to return the current value only.
		val << colname
	}
	
	// Cache the result
	col_names[colname] = val
	
	val
  }
  
  protected def jsonv(v) {
	def result = null
	if ( v ) {
	  if ( !v.equals(null) ) {
		result = "${v.v}"
	  }
	}
	result
  }
}