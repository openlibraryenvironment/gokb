package com.k_int.apis

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes as GA
import org.grails.datastore.gorm.AbstractGormApi
import org.springframework.context.ApplicationContext

/**
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 * API class to add metamethods associated with Security.
 * 
 * This abstract class is an attempt to produce a mechanism to easily extend the classes within
 * Grails and Groovy.
 * 
 * Extenders of this class should declare any methods they wish to add as meta-methods using the public visibility
 * modifier.
 * 
 * Public static methods are added to the class and the first parameter supplied should be the class they are extending.
 * Public none-static methods are added to each instance of the class and the first parameter is the instance itself.
 * 
 * The first parameter is added dynamically and won't be part of the generated meta-signature.
 * 
 * i.e. public myMethod(T instance, String foo) would add the method myMethod(String foo) to the target.
 */
abstract class A_Api <T> {
  protected static final Set<String> EXCLUDES = AbstractGormApi.EXCLUDES + [
    // Extend the list with any that aren't caught here.
  ]
  
  /**
   * Map to allow quick access to the APIs attached to a particular class.
   */
  private static final Map<Class<T>, Map<Class<A_Api>, A_Api>> map = [:].withDefault {Class target ->
    [:].withDefault { Class type ->
      type.newInstance(["targetClass" : (target)])
    }
  }
  
  protected Class<T> targetClass
  
  protected A_Api () {}
  
  protected static ApplicationContext appContext
  
  /**
   * Statically retrieve the application context.
   * 
   * @return ApplicationContext the app context
   */
  protected static ApplicationContext getApplicationContext() {
    if (!appContext) appContext = SCH.servletContext.getAttribute(GA.APPLICATION_CONTEXT)
    appContext
  }
  
  /**
   * Implementation of the groovy property missing.
   * @param name
   * @return
   */
  protected def propertyMissing (String name) {
    this.class.propertyMissing(name)
  }
  
  static {
    
    // Add the method missing in a static context. 
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
  
  /**
   * This is responsible for adding the methods to the targets.
   * 
   * @param targetClass The target class
   * @param apiClass The class containing the methods we are to add.
   */
  public static void addMethods(Class<T> targetClass, Class<A_Api> apiClass) {
    
    // The API.
    A_Api api = A_Api.map.get(targetClass).get(apiClass)
    
    // Should we bind this api to this class?
    if (api.applicableFor(targetClass) ) {
    
      apiClass.getDeclaredMethods().each { Method m ->
        def mods = m.getModifiers()
        def pTypes = m.getParameterTypes()
        
        if (!targetClass.metaClass.getMetaMethod(m.name, pTypes)) {
        
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
  }

  
  /**
   * Allows us to programmatically exclude a class. Defaults to true here.
   * 
   * @param targetClass The target class to check.
   * @return
   */
  protected boolean applicableFor (Class targetClass) {
    return true
  }
}