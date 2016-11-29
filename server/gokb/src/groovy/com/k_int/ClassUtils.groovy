package com.k_int

import org.hibernate.proxy.HibernateProxy
import org.gokb.cred.RefdataCategory
import org.codehaus.groovy.grails.commons.GrailsClassUtils

class ClassUtils {
  public static <T> T deproxy(def element) {
    if (element instanceof HibernateProxy) {
      return (T) ((HibernateProxy) element).getHibernateLazyInitializer().getImplementation();
    }
    return (T) element;
  }

  private static boolean setDateIfPresent(value, obj, prop) {
    def sdf = new java.text.SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
    return setDateIfPresent(value, obj, prop, sdf)
  }

  private static boolean setDateIfPresent(value, obj, prop, sdf) {
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
