package org.gokb.cred
import com.k_int.ClassUtils

class IdentifierNamespace {

  String value
  RefdataValue  datatype

  static mapping = {
    value column:'idns_value'
    datatype column:'idns_datatype'
  }

  static constraints = {
    value (nullable:true, blank:false)
    datatype (nullable:true, blank:false)
  }

  @Override
  public boolean equals(Object obj) {
    if (obj != null) {

      def dep = ClassUtils.deproxy(obj)
      if (dep instanceof IdentifierNamespace) {
        // Check the value attributes.
        return (this.value == dep.value)
      }
    }
    return false
  }
}
