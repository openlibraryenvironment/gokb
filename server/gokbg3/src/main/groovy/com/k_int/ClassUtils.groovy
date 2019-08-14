package com.k_int

import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.hibernate.Hibernate
import org.gokb.cred.RefdataCategory
import grails.util.GrailsClassUtils
import org.gokb.ClassExaminationService
import java.text.SimpleDateFormat
import org.gokb.cred.KBComponent

class ClassUtils {

  @groovy.transform.CompileStatic
  public static <T> T deproxy(Object proxied) {

    T entity = proxied;

    if (entity instanceof HibernateProxy) {
      Hibernate.initialize(entity);
      entity = (T) ((HibernateProxy) entity)
                  .getHibernateLazyInitializer()
                  .getImplementation();
    }

    return entity;
  }

  /**
   * Attempt automatic parsing.
   */
  public static boolean setDateIfPresent(String value, obj, prop) {
    def sdfs = [
      "yyyy-MM-dd' 'HH:mm:ss.SSS",
      "yyyy-MM-dd'T'HH:mm:ss'Z'",
      "yyyy-MM-dd"
    ]
    int num = 0
    SimpleDateFormat sdf = new SimpleDateFormat(sdfs[num])
    
    boolean parsed = setDateIfPresent(value, obj, prop, sdf)
    while (!parsed && ((num++) < (sdfs.size() - 1))) {
      sdf.applyPattern(sdfs[num])
      parsed = setDateIfPresent(value, obj, prop, sdf)
    }
    
    return parsed
  }

  public static boolean setDateIfPresent(String value, obj, prop, SimpleDateFormat sdf) {
    //request.JSON.title.publishedFrom, title, 'publishedFrom', sdf)
    boolean result = false;
    if ( ( value ) && ( value.toString().trim().length() > 0 ) ) {
      try {
        def pd = sdf.parse(value);
        if (pd) {
          obj[prop]=pd;
          result=true;
        }
      }
      catch(Exception e) {
      }
    }
    result;
  }

  public static boolean setRefdataIfPresent(value, objid, prop, cat = null, boolean create = false) {
    boolean result = false
    ClassExaminationService classExaminationService = grails.util.Holders.applicationContext.getBean('classExaminationService')
    def kbc = KBComponent.get(objid)

    if (!cat) {
      cat = classExaminationService.deriveCategoryForProperty(kbc.class.name, prop)
    }

    if ( ( value ) && ( cat ) &&
         ( value.toString().trim().length() > 0 ) &&
         ( ( kbc[prop] == null ) || ( kbc[prop].value != value.trim() ) ) ) {

      def v = null
      if (create) {
        v = RefdataCategory.lookupOrCreate(cat,value)
      }
      else {
        v = RefdataCategory.lookup(cat, value)
      }
    }

    result
  }

  public static boolean setStringIfDifferent(obj, prop, value) {
    boolean result = false;

    if ( ( obj != null ) && ( prop != null ) && ( value ) && ( value.toString().length() > 0 ) ) {

      if ( obj[prop] == value ) {
      }
      else {
        result = true
        obj[prop] = value
      }

    }

    result
  }

}
