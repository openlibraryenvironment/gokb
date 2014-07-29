package com.k_int.apis

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes as GA
import org.gokb.cred.KBDomainInfo
import org.grails.datastore.gorm.AbstractGormApi
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.springframework.context.ApplicationContext
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder as SECCH

/**
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 * 
 * API class to add metamethods associated with Security.
 */
abstract class A_Api <T> {
  protected static final Set<String> EXCLUDES = AbstractGormApi.EXCLUDES + [
    // Extend the list with any that aren't caught here.
  ]
  
  private static final Map<Class<T>, Map<Class<A_Api>, A_Api>> map = [:].withDefault {Class target ->
    [:].withDefault { Class type ->
      type.newInstance(["targetClass" : (target)])
    }
  }
  
  protected Class<T> targetClass
  
  private SecurityApi () {}
  
  protected static ApplicationContext appContext
  protected static ApplicationContext getApplicationContext() {
    if (!appContext) appContext = SCH.servletContext.getAttribute(GA.APPLICATION_CONTEXT)
    appContext
  }
  
  protected def propertyMissing (String name) {
    
    // Try and retrieve a service from the application context.
    if (name =~ /.*Service/) {
      try {
        return getApplicationContext()."${name}"
      } catch (Exception e) {
        throw new MissingPropertyException(name, delegate, e)
      }
    }
    
    // We should always throw a property missing exception if we haven't returned above.
    throw new MissingPropertyException(name, delegate)
  }
  
  public static void addMethods(Class<T> targetClass, Class<A_Api> apiClass) {
    
    // The API.
    A_Api api = A_Api.map.get(targetClass).get(apiClass)
    
    // Should we bind this api to this class?
    if (api.applicableFor(targetClass) ) {
    
      apiClass.getDeclaredMethods().each { Method m ->
        def mods = m.getModifiers()
        def pTypes = m.getParameterTypes()
        
        
        
        if (!m.isSynthetic() && !Modifier.isStatic(mods) && Modifier.isPublic(mods) && !EXCLUDES.contains(m.name)) {
          // Add this method to the target.
          targetClass.metaClass."${m.name}" = { Object[] args ->
            
            def the_args = [delegate] + (args as List)
            api.invokeMethod("${m.name}", the_args as Object[])
          }
        }
      }
    }
  }
  
  protected abstract boolean applicableFor(Class<T> targetClass);
}