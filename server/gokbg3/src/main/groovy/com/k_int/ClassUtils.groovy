package com.k_int

import org.hibernate.proxy.HibernateProxy
import org.gokb.cred.RefdataCategory
import grails.util.GrailsClassUtils
import java.text.SimpleDateFormat

class ClassUtils {
  public static <T> T deproxy(def element) {
    if (element instanceof HibernateProxy) {
      return (T) ((HibernateProxy) element).getHibernateLazyInitializer().getImplementation();
    }
    return (T) element;
  }

  /**
   * Attempt automatic parsing.
   */
  public static boolean setDateIfPresent(String value, obj, prop) {
    def sdfs = [
      "yyyy-MM-dd' 'HH:mm:ss.SSS",
      "yyyy-MM-dd'T'HH:mm:ss'Z'"
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

  public static boolean setRefdataIfPresent(value, obj, prop, cat = null) {
    boolean result = false
    if (!cat) {
      cat = RefdataCategory.derriveCategoryForProperty(obj, prop)
    }

    if ( ( value ) && ( cat ) &&
         ( value.toString().trim().length() > 0 ) &&
         ( ( obj[prop] == null ) || ( obj[prop].value != value.trim() ) ) ) {
      def v = RefdataCategory.lookupOrCreate(cat,value)
      obj[prop] = v
      result = true
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
