package org.gokb.validation.types

interface I_ColumnValidationRule {

  public boolean validate (final result, final col_positions, final originalDefinitions)
  public String getSeverity()
}
