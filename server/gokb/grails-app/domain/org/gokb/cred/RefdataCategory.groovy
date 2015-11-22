package org.gokb.cred


import groovy.util.logging.*

@Log4j
class RefdataCategory {

  String desc
  String label
  Set values

  static mapping = {
         id column:'rdc_id'
    version column:'rdc_version'
      label column:'rdc_label'
       desc column:'rdc_description', index:'rdc_description_idx'
     values sort:'value', order:'asc'

  }

  static hasMany = [
    values:RefdataValue
  ]

  static mappedBy = [
    values:'owner'
  ]

  static constraints = {
    label(nullable:true, blank:true)
  }

  static RefdataValue lookupOrCreate(category_name, value) {
    return lookupOrCreate(category_name,value,null);
  }

  static RefdataValue lookupOrCreate(category_name, value, sortkey) {
  
    if ( value == null )
      throw new RuntimeException("Request to lookupOrCreate null value in category ${category_name}");

    def result = null

    // The category.
    // RefdataCategory.withTransaction { status ->

      log.debug("Attempting to locate category ${category_name}");

      def cats = RefdataCategory.executeQuery('select c from RefdataCategory as c where c.desc = ?',category_name);
      def cat = null;

      if ( cats.size() == 0 ) {
        cat = new RefdataCategory(desc:category_name)
        cat.save(failOnError:true, flush:true)
        log.debug("Create new refdataCategory(${category_name}) = ${cat.id}");
      }
      else if ( cats.size() == 1 ) {
        cat = cats[0]
        result = RefdataValue.findByOwnerAndValueIlike(cat, value)
      }
      else {
        throw new RuntimeException("Multiple matching refdata category names");
      }

      if ( !result ) {
        // Create and save a new refdata value.
        result = new RefdataValue(owner:cat, value:value, sortKey:sortkey)
        result.save(failOnError:true, flush:true)
        result.refresh();
        log.debug("Create new refdataValue(${category_name},${value},${sortkey}) = ${result.id}");
      }
    // }

    assert result != null

    // return the refdata value.
    result
  }

//  def availableActions() {
//    [ [ code:'object::delete' , label: 'Delete' ] ]
//  }

  static String getOID(category_name, value) {
    String result = null
    def cat = RefdataCategory.findByDesc(category_name);
    if ( cat != null ) {
      def v = RefdataValue.findByOwnerAndValueIlike(cat, value)
      if ( v != null ) {
        result = "org.gokb.cred.RefdataValue:${v.id}"
      }
    }
  }
}
