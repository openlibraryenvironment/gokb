package com.k_int.apis;

import javax.persistence.Transient;
import com.k_int.ClassUtils

import grails.util.GrailsNameUtils;

public class GrailsDomainHelpersApi<T> extends A_Api<T> {

  public String getNiceName (T component) {
    GrailsNameUtils.getNaturalName(getClassName(component))
  }
  
  public String getClassName (T component) {
    component.getMetaClass().getTheClass().getName();
  }
  
  public boolean isInstanceOf (T component, Class testCase) {
    component.getMetaClass().getTheClass().isAssignableFrom(testCase)
  }
  
  public static <E> E deproxy(Class<T> clazz, def element) {
    ClassUtils.deproxy(element)
  }
}
