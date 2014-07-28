package org.gokb

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
class SecurityApi <T> {
  private static final Set<String> EXCLUDES = AbstractGormApi.EXCLUDES + [
    // Extend the list with any that aren't caught here.
  ]
  
  private static final Map<Class<T>, SecurityApi <T>> map = [:].withDefault { Class key ->
    new SecurityApi (["targetClass" : (key)])
  }
  
  private Class<T> targetClass
  
  private SecurityApi () {}
  
  private static AclUtilService aclUtilService
  private static AclUtilService getAclUtilService() {
    if (!aclUtilService) aclUtilService = getApplicationContext().aclUtilService
    aclUtilService
  }
  
  static ApplicationContext appContext
  private static ApplicationContext getApplicationContext() {
    if (!appContext) appContext = SCH.servletContext.getAttribute(GA.APPLICATION_CONTEXT)
    appContext
  }
  
  public boolean isEditable (T instance) {
    def domain_record_info = KBDomainInfo.findByDcName(instance.class.name)
    
    Authentication auth = SECCH.context.authentication
    if (domain_record_info && domain_record_info) {
      boolean can_edit = getAclUtilService().hasPermission(
        auth,
        domain_record_info,
        org.springframework.security.acls.domain.BasePermission.WRITE)
      
      return can_edit
    }
    
    // Default to true.
    true
  }
  
  public static addMethods(Class<T> targetClass) {
    final methodsToAdd = getDeclaredMethods().each { Method m ->
      def mods = m.getModifiers()
      def pTypes = m.getParameterTypes()
      
      if (!m.isSynthetic() && !Modifier.isStatic(mods) && Modifier.isPublic(mods) && !EXCLUDES.contains(m.name)) {
        // Add this method to the target.
        targetClass.metaClass."${m.name}" = { Object[] args ->
          
          def the_args = [delegate] + (args as List)
          SecurityApi.map.get(targetClass).invokeMethod("${m.name}", the_args as Object[])
        }
      }
    }
  }
}