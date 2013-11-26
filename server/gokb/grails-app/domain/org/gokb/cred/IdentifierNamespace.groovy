package org.gokb.cred

class IdentifierNamespace {

  String value

  static mapping = {
    value column:'idns_value'
  }

  static constraints = {
	value (nullable:true, blank:false)
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
