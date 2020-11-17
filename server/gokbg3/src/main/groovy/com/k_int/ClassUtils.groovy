package com.k_int

import org.hibernate.proxy.HibernateProxy
import org.hibernate.proxy.LazyInitializer
import org.hibernate.Hibernate
import org.gokb.cred.RefdataCategory
import org.gokb.cred.RefdataValue
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
    LocalDateTime ldt = null
    DateTimeFormatter dateformatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("" + "[uuuu-MM-dd' 'HH:mm:ss.SSS]" + "[uuuu-MM-dd'T'HH:mm:ss'Z']").withResolverStyle(ResolverStyle.STRICT)

    if (value && value.toString().trim()) {
      if (value instanceof LocalDateTime) {
        ldt = value
      }
      else if (value instanceof LocalDate) {
        ldt = value.atStartOfDay()
      }
      else {
        try {
          ldt = LocalDateTime.parse(value, datetimeformatter)
        }
        catch (Exception e) {
        }

        if (!ldt) {
          try {
            ldt = LocalDate.parse(value, dateformatter).atStartOfDay()
          }
          catch (Exception e) {
          }
        }
      }

      if (ldt) {
        Date instant = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant())
        if (instant != obj[prop]) {
          obj[prop] = instant
          return true
        }
      }
    }
    return false
  }

  public static boolean updateDateField(def value, obj, prop) {
    LocalDateTime ldt = null
    DateTimeFormatter dateformatter = DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)
    DateTimeFormatter datetimeformatter = DateTimeFormatter.ofPattern("" + "[uuuu-MM-dd' 'HH:mm:ss.SSS]" + "[uuuu-MM-dd'T'HH:mm:ss'Z']").withResolverStyle(ResolverStyle.STRICT)

    if (value && value.toString().trim()) {
      if (value instanceof LocalDateTime) {
        ldt = value
      }
      else if (value instanceof LocalDate) {
        ldt = value.atStartOfDay()
      }
      else {
        try {
          ldt = LocalDateTime.parse(value, datetimeformatter)
        }
        catch (Exception e) {
        }

        if (!ldt) {
          try {
            ldt = LocalDate.parse(value, dateformatter).atStartOfDay()
          }
          catch (Exception e) {
          }
        }
      }

      if (ldt) {
        Date instant = Date.from(ldt.atZone(ZoneId.systemDefault()).toInstant())
        if (instant != obj[prop]) {
          obj[prop] = instant
          return true
        }
      }
    }
    else if (!value) {
      boolean result = obj[prop] != null
      obj[prop] = null
      return result
    }
    return false
  }

  public static boolean setDateIfPresent(String value, obj, prop, SimpleDateFormat sdf) {
    //request.JSON.title.publishedFrom, title, 'publishedFrom', sdf)
    boolean result = false;
    if (value && value.toString().trim()) {
      try {
        def pd = sdf.parse(value)
        if (pd) {
          result = obj[prop] != pd
          obj[prop] = pd
        }
      }
      catch (Exception e) {
      }
    }
    result
  }

  public static boolean setRefdataIfPresent(value, kbc, prop, cat = null, boolean create = false) {
    boolean result = false
    ClassExaminationService classExaminationService = grails.util.Holders.applicationContext.getBean('classExaminationService')
    def v = null

    if (!cat) {
      cat = classExaminationService.deriveCategoryForProperty(kbc.class.name, prop)
    }

    if (value instanceof String && value.trim() && cat) {
      if (create) {
        v = RefdataCategory.lookupOrCreate(cat, value)
      }
      else {
        v = RefdataCategory.lookup(cat, value)
      }
    }
    else if (value instanceof Integer && cat) {
      try {
        def candidate = RefdataValue.get(value)

        if (candidate && candidate.owner == cat) {
          v = candidate
        }
      }
      catch (Exception e) {

      }
    }
    else if (value instanceof Map && cat) {
      if (value.id) {
        try {
          def candidate = RefdataCategory.get(value)

          if (candidate && candidate.owner == cat) {
            v = candidate
          }
        }
        catch (Exception e) {

        }
      }
      else if (value.name || value.value) {
        v = RefdataCategory.lookup(cat, (value.name ?: value.value))
      }
    }

    if (v) {
      result = kbc[prop] != v
      kbc[prop] = v
    }
    result
  }

  public static boolean setStringIfDifferent(obj, prop, value) {
    if ((obj != null) && (prop != null) && (value) && (value.toString().length() > 0))
      if (obj[prop] != value) {
        obj[prop] = value
        return true
      }
    return false
  }

  public static boolean setBooleanIfDifferent(obj, prop, value) {
    if ((obj != null) && (prop != null) && (value != null)) {
      if (obj[prop] != value) {
        obj[prop] = value
        return true
      }
    }
    return false
  }
}
