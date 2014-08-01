package com.k_int.apis

import org.gokb.cred.KBDomainInfo
import org.springframework.security.acls.model.Permission
import org.springframework.security.core.context.SecurityContextHolder as SECCH

/**
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 * 
 * API class to add metamethods associated with Security.
 */
class SecurityApi <T> extends A_Api<T> {
  
  private SecurityApi () {}
  
  public static boolean isEditable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.WRITE)
  }
  
  public static boolean isCreatable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.CREATE)
  }
  
  public static boolean isReadable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.READ)
  }
  
  public static boolean isDeletable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.DELETE)
  }
  
  public static boolean isAdministerable (Class<T> clazz, boolean defaultTo = true) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.ADMINISTRATION)
  }
  
  public boolean isEditable(T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isEditable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isCreatable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isCreatable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isReadable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isReadable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isDeletable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isDeletable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public boolean isAdministerable (T component, boolean defaultTo = true) {
    
    boolean allowed = !(component.respondsTo('isSystemComponent') && component.isSystemComponent())
    if (allowed) {
      allowed = SecurityApi.isAdministerable (component.getClass(), defaultTo)
    }
    allowed
  }
  
  public static boolean hasPermission(Class<T> clazz, Permission p, boolean defaultTo = true) {
    def domain_record_info = KBDomainInfo.findByDcName(clazz.name)
    if (domain_record_info) {
      boolean can_edit = this.aclUtilService.hasPermission(
        SECCH.context.authentication,
        domain_record_info,
        p)
      
      return can_edit
    }
    
    // Return the default value if not found.
    defaultTo
  }

  @Override
  protected boolean applicableFor (Class targetClass) {
    // Valid for all classes.
    return true
  }
}