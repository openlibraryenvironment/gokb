package com.k_int

import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.hibernate.Hibernate
import org.gokb.cred.RefdataCategory
import grails.util.GrailsClassUtils
import org.gokb.ClassExaminationService
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.ZoneId
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
  public static boolean setDateIfPresent(def value, obj, prop) {
    boolean result = false
    LocalDateTime ldt = null
    DateTimeFormatter dateformatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("" + "[uuuu-MM-dd' 'HH:mm:ss.SSS]" + "[uuuu-MM-dd'T'HH:mm:ss'Z']").withResolverStyle(ResolverStyle.STRICT)

    if ( value && value.toString().trim() ) {
      if (value instanceof LocalDateTime) {
        ldt = value
      }
      else if (value instanceof LocalDate) {
        ldt = value.atStartOfDay()
      }
      else {
        try {
          ldt = LocalDateTime.parse(value, datetimeformatter)
          result = true
        }
        catch ( Exception e ) {
        }

        if (!ldt) {
          try {
            ldt = LocalDate.parse(value, dateformatter).atStartOfDay()
            result = true
          }
          catch ( Exception e ) {
          }
        }
      }

      if (ldt) {
        obj[prop] = Date.from( ldt.atZone(ZoneId.systemDefault()).toInstant())
      }
    }

    return result
  }

  public static boolean updateDateField(def value, obj, prop) {
    boolean result = false
    LocalDateTime ldt = null
    DateTimeFormatter dateformatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("" + "[uuuu-MM-dd' 'HH:mm:ss.SSS]" + "[uuuu-MM-dd'T'HH:mm:ss'Z']").withResolverStyle(ResolverStyle.STRICT)

    if ( value && value.toString().trim() ) {
      if (value instanceof LocalDateTime) {
        ldt = value
      }
      else if (value instanceof LocalDate) {
        ldt = value.atStartOfDay()
      }
      else {
        try {
          ldt = LocalDateTime.parse(value, datetimeformatter)
          result = true
        }
        catch ( Exception e ) {
        }

        if (!ldt) {
          try {
            ldt = LocalDate.parse(value, dateformatter).atStartOfDay()
            result = true
          }
          catch ( Exception e ) {
          }
        }
      }

      if (ldt) {
        obj[prop] = Date.from( ldt.atZone(ZoneId.systemDefault()).toInstant())
      }
    }
    else if (!value) {
      obj[prop] = null
    }

    return result
  }

  public static boolean setDateIfPresent(String value, obj, prop, SimpleDateFormat sdf) {
    //request.JSON.title.publishedFrom, title, 'publishedFrom', sdf)
    boolean result = false;
    if ( value && value.toString().trim() ) {
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

  public static boolean setRefdataIfPresent(value, kbc, prop, cat = null, boolean create = false) {
    boolean result = false
    ClassExaminationService classExaminationService = grails.util.Holders.applicationContext.getBean('classExaminationService')

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

      if (v) {
        kbc[prop] = v
        result = true
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
