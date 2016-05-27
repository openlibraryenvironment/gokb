package com.k_int.apis

import groovy.util.logging.Log4j

import grails.plugin.springsecurity.SpringSecurityUtils
import org.gokb.cred.KBDomainInfo
import org.springframework.security.acls.model.Permission
import org.springframework.security.core.context.SecurityContextHolder as SECCH

/** 
 * <p>API class to add meta-methods associated with Security.</p>
 * 
 * <p>Creates a static hasPermission (Permission p, [defaultTo])</p>
 * <p>Also adds the following in both a static and none static context, all with an optional default value
 *  if no domain class has been declared as responsible for the permissions.</p>
 * <ul>
 *  <li>isEditable ([defaultTo])
 *  <li>isCreatable ([defaultTo])
 *  <li>isReadable ([defaultTo])
 *  <li>isAdministerable ([defaultTo])
 * </ul>
 * 
 * <p>Also adds the following in both a static and none static context:</p>
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 */
@Log4j
class SecurityApi <T> extends A_Api<T> {
  
  private SecurityApi () {}
  
  public static boolean isTypeEditable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.WRITE)
  }
  
  public static boolean isTypeCreatable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.CREATE)
  }
  
  public static boolean isTypeReadable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.READ)
  }
  
  public static boolean isTypeDeletable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.DELETE)
  }
  
  public static boolean isTypeAdministerable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.ADMINISTRATION)
  }
  
  public boolean isEditable(T component, boolean defaultTo = true) {
    
    boolean allowed = defaultTo

    // Calling this method on an ojbect that has no id, and therefore hasn't been saved
    // will instead route through isCreatable as this is a create and not an edit.
    if ( component ) {
      if (component.id == null) 
        return isCreatable (component, defaultTo)
    
      allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
      if (allowed) {
        allowed = SecurityApi.isTypeEditable (component.getClass(), defaultTo)
      }
    }
    else {
      System.err.println("Null component in call to SecurityApi::isEditable");
    }

    allowed
  }
  
  public boolean isCreatable (T component, boolean defaultTo = true) {
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isTypeCreatable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isReadable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isTypeReadable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isDeletable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isTypeDeletable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isAdministerable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isTypeAdministerable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public static boolean hasPermission(Class<T> clazz, Permission p, boolean defaultTo = true) {
    
    // Super users can do everything...
    if (SpringSecurityUtils.ifAnyGranted('ROLE_SUPERUSER')) return true
    
    def domain_record_info = KBDomainInfo.findByDcName(clazz.name)

    if (domain_record_info) {
      boolean can_edit = this.aclUtilService.hasPermission( SECCH.context.authentication, domain_record_info, p)
      return can_edit
    }
    
    // Return the default value if not found.
    defaultTo
  }
}
