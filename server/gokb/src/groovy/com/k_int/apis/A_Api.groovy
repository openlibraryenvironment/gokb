package com.k_int.apis

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes as GA
import org.grails.datastore.gorm.AbstractGormApi
import org.springframework.context.ApplicationContext

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
  
  protected A_Api () {}
  
  protected static ApplicationContext appContext
  protected static ApplicationContext getApplicationContext() {
    if (!appContext) appContext = SCH.servletContext.getAttribute(GA.APPLICATION_CONTEXT)
    appContext
  }
  
  protected def propertyMissing (String name) {
    this.class.propertyMissing(name)
  }
  
  static {
    getMetaClass()."static".propertyMissing = { name ->
      // Try and retrieve a service from the application context.
      if (name =~ /.*Service/) {
        try {
          return getApplicationContext()."${name}"
        } catch (Exception e) {
          throw new MissingPropertyException(name, this, e)
        }
      }
      
      // We should always throw a property missing exception if we haven't returned above.
      throw new MissingPropertyException(name, this)
    }
  }
  
  public static void addMethods(Class<T> targetClass, Class<A_Api> apiClass) {
    
    // The API.
    A_Api api = A_Api.map.get(targetClass).get(apiClass)
    
    // Should we bind this api to this class?
    if (api.applicableFor(targetClass) ) {
    
      apiClass.getDeclaredMethods().each { Method m ->
        def mods = m.getModifiers()
        def pTypes = m.getParameterTypes()
        
        
        
        if (!m.isSynthetic() && Modifier.isPublic(mods) && !EXCLUDES.contains(m.name)) {
          
          if (!Modifier.isStatic(mods)) {
          
            // Add this method to the target.
            targetClass.metaClass."${m.name}" = { args ->
              
              def the_args = args ?: [] as List
              
              
              // Prepend the new value.
              the_args.add(0, delegate)
              api.invokeMethod("${m.name}", the_args.toArray())
            }
          } else {
            // Add to the static scope.
            targetClass.metaClass.static."${m.name}" = { args ->
              
              def the_args = args ?: [] as List
              
              // Prepend the new value.
              the_args.add(0, delegate.class)
              apiClass.invokeMethod("${m.name}", the_args.toArray())
            }
          }
        }
      }
    }
  }
  
  protected abstract boolean applicableFor(Class<T> targetClass);
}