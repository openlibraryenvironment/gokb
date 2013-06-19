package org.gokb.validation.types

interface I_DeferredRowValidationRule extends I_ValidationRule {

  public void process (final col_positions, final rowNum, final datarow)
  public boolean validate (final result)
}
