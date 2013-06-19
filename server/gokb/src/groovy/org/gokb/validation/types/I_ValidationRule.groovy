package org.gokb.validation.types

interface I_ValidationRule {
  
  public static final int SEVERITY_ERROR = 10
  public static final int SEVERITY_WARNING = 5
  
  /**
   * The severity rating of the error.
   * @return
   */
  public int getSeverity()
  
  /**
   * The method to return an instance of the rule.
   */
  public I_ValidationRule getInstance(args)
}