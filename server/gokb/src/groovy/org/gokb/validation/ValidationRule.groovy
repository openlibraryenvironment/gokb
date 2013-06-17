package org.gokb.validation

interface ValidationRule {
  
  /**
   * Do the validation for the particular type.
   * @return
   */
  public boolean valid(def args)
  public String getErrorMessage()
  public String getErrorType()
}