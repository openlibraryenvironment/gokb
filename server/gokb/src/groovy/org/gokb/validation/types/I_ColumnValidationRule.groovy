package org.gokb.validation.types

interface I_ColumnValidationRule {

  public boolean validate (final result, final col_positions)
  public String getSeverity()
}
