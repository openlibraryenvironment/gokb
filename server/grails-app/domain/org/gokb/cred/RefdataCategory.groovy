package org.gokb.cred


import groovy.util.logging.*
import grails.util.GrailsNameUtils
import javax.persistence.Transient
import org.grails.datastore.mapping.model.*

@Slf4j
class RefdataCategory {

  public static rdv_cache = [:]
  private static rdc_cache = [:]

  String desc
  String label
  Set values

  static mapping = {
    id column: 'rdc_id'
    version column: 'rdc_version'
    label column: 'rdc_label'
    desc column: 'rdc_description', index: 'rdc_description_idx'
    values sort: 'value', order: 'asc'

  }

  static hasMany = [
    values: RefdataValue
  ]

  static mappedBy = [
    values: 'owner'
  ]

  static constraints = {
    label(nullable: true, blank: true)
  }

  String getLogEntityId() {
    "${this.class.name}:${id}"
  }

  public static final String restPath = "/refdata/categories"

  static def lookup(category_name, value, def sortkey = null) {

    // log.debug("lookup(${category_name}, ${value}, ${sortkey})");

    if ((value == null) || (category_name == null))
      throw new RuntimeException("Request to lookupOrCreate null value in category ${category_name}")

    def result = null

    def rdv_cache_key = category_name + ':' + value + ':' + sortkey
    def rdv_id = rdv_cache[rdv_cache_key]
    if (rdv_id && rdv_id instanceof Long) {
      result = RefdataValue.get(rdv_id)
    } else if (!rdv_id instanceof Long) {
      throw new RuntimeException("Got a string value from rdv_cache for ${category_name}, ${value}!")
    } else {
      // The category.
      def cats = RefdataCategory.executeQuery('select c from RefdataCategory as c where lower(c.desc) = :desc', [desc: category_name.toLowerCase()])
      def cat = null

      if (cats.size() == 0) {
        return result
      } else if (cats.size() == 1) {
        cat = cats[0]
        result = RefdataValue.findByOwnerAndValueIlike(cat, value)
      } else {
        throw new RuntimeException("Multiple matching refdata category names")
      }

      if (result) {
        rdv_cache[rdv_cache_key] = result.id
      }
    }
    // return the refdata value.
    result
  }

  static RefdataValue[] lookup(category_name) {
    if (category_name == null)
      throw new RuntimeException("Request to lookup category ${category_name}")

    def result = null;

    def rdc_cache_key = category_name
    def rdc_id = rdc_cache[rdc_cache_key]
    if (rdc_id && rdc_id instanceof Long) {
      // cache hit
      result = RefdataValue.findAllByOwner(rdc_id)
    } else if (!rdc_id instanceof Long) {
      throw new RuntimeException("Got a wrong value from rdv_cache for ${category_name}!")
    } else {
      // The category.
      def cat = RefdataCategory.executeQuery('select c from RefdataCategory as c where lower(c.desc) = :desc', [desc: category_name.toLowerCase()])

      if (cat.size() == 0) {
        return result
      } else {
        result = RefdataValue.findAllByOwner(cat)
      }

      if (result) {
        rdc_cache[rdc_cache_key] = result.id
      }
    }

    // return the refdata values.
    result
  }

  static RefdataValue lookupOrCreate(category_name, value) {
    return lookupOrCreate(category_name, value, null)
  }

  static RefdataValue lookupOrCreate(category_name, value, sortkey) {

    if ((value == null) || (category_name == null))
      throw new RuntimeException("Request to lookupOrCreate null value in category ${category_name}")

    def result = null;

    def rdv_cache_key = category_name + ':' + value + ':' + sortkey
    def rdv_id = rdv_cache[rdv_cache_key]
    if (rdv_id && rdv_id instanceof Long) {
      result = RefdataValue.get(rdv_id)
    } else if (!rdv_id instanceof Long) {
      throw new RuntimeException("Got a string value from rdv_cache for ${category_name}, ${value}!");
    } else {
      def cats = RefdataCategory.executeQuery('select c from RefdataCategory as c where lower(c.desc) = :desc', [desc: category_name.toLowerCase()])
      def cat = null

      if (cats.size() == 0) {
        cat = new RefdataCategory(desc: category_name, label: category_name)
        if (cat.save(failOnError: true, flush: true)) {
        } else {
          log.error("Problem creating new category ${category_name}")
          cat.errors.each {
            log.error("Problem: ${it}");
          }
        }
      } else if (cats.size() == 1) {
        cat = cats[0]
        result = RefdataValue.findByOwnerAndValueIlike(cat, value)
      } else {
        throw new RuntimeException("Multiple matching refdata category names")
      }

      if (!result) {
        result = new RefdataValue(owner: cat, value: value, sortKey: sortkey)
        if (result.save(failOnError: true, flush: true)) {
        } else {
          result.errors.each {
            log.error("Problem: ${it}")
          }
        }
      } else {
        rdv_cache[rdv_cache_key] = result.id
      }
      // }
    }


    assert result != null

    // return the refdata value.
    result
  }


  static RefdataValue lookupOrCreate(String category_name, Map sortedValues) {
    for (def entry in sortedValues){
      lookupOrCreate(category_name, entry.getKey(), entry.getValue())
    }
  }


//  def availableActions() {
//    [ [ code:'object::delete' , label: 'Delete' ] ]
//  }

  static String getOID(category_name, value) {
    String result = null
    def cat = RefdataCategory.findByDesc(category_name)
    if (cat != null && value != null) {
      def v = RefdataValue.findByOwnerAndValueIlike(cat, value)
      if (v != null) {
        result = "org.gokb.cred.RefdataValue:${v.id}"
      }
    }
  }

}
