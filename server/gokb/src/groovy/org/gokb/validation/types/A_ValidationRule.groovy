package org.gokb.validation.types

import org.codehaus.groovy.grails.web.json.JSONObject

abstract class A_ValidationRule {

  public static final String SEVERITY_ERROR = "error"
  public static final String SEVERITY_WARNING = "notice"

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
    Map message = ['type' : getSeverity(), 'sub_type' : getType()] + getMessageProperties()

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

  protected String getRowValue(datarow, col_positions, colname) {

    String result = null
    if ( col_positions[colname] != null ) {
      result = jsonv(datarow.cells[col_positions[colname]])
    }
    result
  }

  protected def jsonv(v) {
    def result = null
    
    // Thoroughly check for nulls.
    if (v && !(v.equals(null) || JSONObject.NULL.equals(v) ) ) {
      if (v.v != null && !JSONObject.NULL.equals(v.v)) {
        result = "${v.v}"
      }
    }
    result
  }
}