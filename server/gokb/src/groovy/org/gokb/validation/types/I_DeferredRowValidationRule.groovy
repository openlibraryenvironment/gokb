package org.gokb.validation.types

interface I_DeferredRowValidationRule {

  public void process (final col_positions, final rowNum, final datarow)
  public void validate (final result)
}
