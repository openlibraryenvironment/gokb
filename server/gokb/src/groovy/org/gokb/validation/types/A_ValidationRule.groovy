package org.gokb.validation.types

abstract class A_ValidationRule {
  
  public static final int SEVERITY_ERROR = 10
  public static final int SEVERITY_WARNING = 5
  private boolean errorTriggered = false
  
  protected isErrorTriggered() {
	return errorTriggered
  }
  
  /**
   * The severity rating of the error.
   * @return
   */
  protected abstract int getSeverity()
  
  /**
   * Return the type to be sent with error messages.
   */
  protected abstract String getType()
  
  /**
   * Return the extra information to be sent with error messages.
   */
  protected abstract Map getMessageExtras ()
  
  /**
   * Add an error message.
   */
  protected void addError(final result, String messageText) {
	
	// Get the extras added by this rule.
	Map message = getMessageExtras()
	
	// Add the other required items.
	message['severity'] = getSeverity()
	message['type']		= getType()
	message['text']		= messageText
	
	// If messages isn't set then default to empty list.
	if (result.messages == null) {
	  result.messages = []
	}
	
	// Set the status and add the message.
	result.status = false
	result.messages.add(message)
	
	// Set the error triggered flag.
	errorTriggered = true
  }
  
  protected def getRowValue(datarow, col_positions, colname) {
	def result = null
	if ( col_positions[colname] != null ) {
	  result = jsonv(datarow.cells[col_positions[colname]])
	}
	result
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