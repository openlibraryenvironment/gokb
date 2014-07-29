package com.k_int.apis

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes as GA
import org.gokb.cred.KBDomainInfo
import org.grails.datastore.gorm.AbstractGormApi
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.springframework.context.ApplicationContext
import org.springframework.security.acls.model.Permission
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder as SECCH

/**
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 * 
 * API class to add metamethods associated with Security.
 */
class SecurityApi <T> extends A_Api<T> {
  
  private SecurityApi () {}
  
  public static boolean isEditable (Class<T> clazz) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.WRITE)
  }
  
  public static boolean isCreatable (Class<T> clazz) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.CREATE)
  }
  
  public static isReadable (Class<T> clazz) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.READ)
  }
  
  public boolean isDeletable (Class<T> clazz) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.DELETE)
  }
  
  public boolean isAdministerable (Class<T> clazz) {
    hasPermission (clazz, org.springframework.security.acls.domain.BasePermission.ADMINISTRATION)
  }
  
  public static boolean hasPermission(Class<T> clazz, Permission p) {
    def domain_record_info = KBDomainInfo.findByDcName(clazz.name)
    if (domain_record_info) {
      boolean can_edit = this.aclUtilService.hasPermission(
        SECCH.context.authentication,
        domain_record_info,
        p)
      
      return can_edit
    }
    
    // Default to true, as any class without an associated model should be ignored.
    true
  }

  @Override
  protected boolean applicableFor (Class targetClass) {
    // Valid for all classes.
    return true
  }
}