package com.k_int.apis;

import javax.persistence.Transient;

import com.k_int.ClassUtils

import grails.util.GrailsNameUtils;

public class GrailsDomainHelpersApi<T> extends A_Api<T> {

  public String getNiceName (T component) {
    GrailsNameUtils.getNaturalName(getClassName(component))
  }

  /*
   THIS method was overriding the className property on spring security ACLClass domain object, causing 
   untold horror. Therefore renamed getComponentClassName.
  */
  public String getClassName (T component) {
    deproxy(null, component).getClass().getName();
  }
  
  /*
  Commenting out for now.. Seems to be causing problems
  public boolean isInstanceOf (T component, Class testCase) {
    component.getMetaClass().getTheClass().isAssignableFrom(testCase)
  }
  */
  
  public static <E> E deproxy(Class<T> clazz, def element) {
    ClassUtils.deproxy(element)
  }
  
  protected boolean applicableFor (Class c) {
    if ( c.name.startsWith('org.gokb') ) {
      return true
    }
    
    return false
  }
}
