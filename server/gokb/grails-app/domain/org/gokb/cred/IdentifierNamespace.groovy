package org.gokb.cred
import com.k_int.ClassUtils

class IdentifierNamespace {

  String value
  RefdataValue  datatype
  String family

  static mapping = {
    value column:'idns_value'
    datatype column:'idns_datatype'
    family column:'idns_family'
  }

  static constraints = {
    // II: Want this, but need to tidy live first :: value (nullable:true, blank:false, unique:true)
    value (nullable:true, blank:false)
    datatype (nullable:true, blank:false)
    family (nullable:true, blank:false)
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

  static def refdataFind(params) {
    def result = [];
    def ql = null;
    // ql = TitleInstance.findAllByNameIlike("${params.q}%",params)
    // Return all titles where the title matches (Left anchor) OR there is an identifier for the title matching what is input
    ql = IdentifierNamespace.executeQuery("select t.id, t.value from IdentifierNamespace as t where lower(t.value) like ?", ["${params.q?.toLowerCase()}%"],[max:20]);

    if ( ql ) {
      ql.each { t ->
        result.add([id:"org.gokb.cred.IdentifierNamespace:${t[0]}",text:"${t[1]} "])
      }
    }

    result
  }

  def beforeInsert() {
    value = value.toLowerCase()
  }


}
