package org.gokb.cred
import com.k_int.ClassUtils

class IdentifierNamespace {

  String name
  String value
  RefdataValue  datatype
  RefdataValue targetType
  String pattern
  String family

  static mapping = {
    name column:'idns_name'
    value column:'idns_value'
    datatype column:'idns_datatype'
    targetType column:'idns_targettype'
    family column:'idns_family'
    pattern column:'idns_pattern'
  }

  static constraints = {
    // II: Want this, but need to tidy live first :: value (nullable:true, blank:false, unique:true)
    name (nullable:true)
    value (nullable:true, blank:false, unique:true)
    datatype (nullable:true, blank:false)
    family (nullable:true, blank:false)
    pattern (nullable:true, blank:false)
    targetType (nullable:true, blank:false)
  }

  public static final String restPath = "/namespaces"
  // used by @gokbg3.RestMappingService.selectJsonLabel
  public static final String jsonLabel = "name"

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

  public String toString() {
    "${name ?: value}".toString()
  }
}
