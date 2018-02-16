package org.gokb.cred


import groovy.util.logging.*
import grails.util.GrailsNameUtils

@Log4j
class RefdataCategory {

  public static rdv_cache = [:]

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

    // log.debug("lookupOrCreate(${category_name}, ${value}, ${sortkey})");
  
    if ( ( value == null ) || ( category_name == null ) )
      throw new RuntimeException("Request to lookupOrCreate null value in category ${category_name}");

    def result = null;

    def rdv_cache_key = category_name+':'+value+':'+sortkey
    def rdv_id  = rdv_cache[rdv_cache_key]
    if ( rdv_id ) {
      result = RefdataValue.get(rdv_id);
    }
    else {
      // The category.
      RefdataCategory.withTransaction { status ->
  
  
        def cats = RefdataCategory.executeQuery('select c from RefdataCategory as c where c.desc = ?',category_name);
        def cat = null;
  
        if ( cats.size() == 0 ) {
          log.debug("Create new refdata category ${category_name}");
          cat = new RefdataCategory(desc:category_name, label:category_name)
          if ( cat.save(failOnError:true, flush:true) ) {
          }
          else {
            log.error("Problem creating new category ${category_name}");
            cat.errors.each {
              log.error("Problem: ${it}");
            }
          }
  
          // log.debug("Create new refdataCategory(${category_name}) = ${cat.id}");
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
          log.info("Attempt to create new refdataValue(${category_name},${value},${sortkey})");
          result = new RefdataValue(owner:cat, value:value, sortKey:sortkey)
          if ( result.save(failOnError:true, flush:true) ) {
          }
          else {
            log.debug("Problem saving new refdata item");
            result.errors.each {
              log.error("Problem: ${it}");
            }
          }
        }
        else {
          rdv_cache[rdv_cache_key] = result.id
        }
      }
    }
    

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
  
  static String derriveCategoryForProperty ( obj, String propName ) {
    String propertyDef
    
    // Default to this class.
    def moreTests = obj.domainClass.clazz
    while (moreTests) {
        
      // Read the property...
      if (moreTests.metaClass.properties.find {it.name == propName}) {
        propertyDef = "${moreTests.simpleName}"
      
        // Get the superclass.
        moreTests = moreTests.getSuperclass()
      } else {
        moreTests = false
      }
    }
    
    propertyDef ? "${propertyDef}.${GrailsNameUtils.getClassName(propName)}" : null
    
  }
}
