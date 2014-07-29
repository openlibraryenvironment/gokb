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
class SecurityApi <T> extends A_Api {
  
  private SecurityApi () {}
  
  public boolean isEditable (T instance) {
    def domain_record_info = KBDomainInfo.findByDcName(instance.class.name)
    
    Authentication auth = SECCH.context.authentication
    if (domain_record_info && domain_record_info) {
      boolean can_edit = aclUtilService.hasPermission(
        auth,
        domain_record_info,
        org.springframework.security.acls.domain.BasePermission.WRITE)
      
      return can_edit
    }
    
    // Default to true.
    true
  }

  @Override
  protected boolean applicableFor (Class targetClass) {
    // Valid for all classes.
    return true
  }
}