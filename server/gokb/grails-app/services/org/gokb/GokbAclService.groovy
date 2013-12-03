package org.gokb

import grails.util.GrailsNameUtils

import java.beans.PropertyDescriptor

import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission


class GokbAclService extends AclUtilService {
  
  /** Ensure we set as none transactional **/
  static transactional = false
  
  def aclLookupStrategy
  
  Acl readAclSilently(domainObject) {
    
    // Just return null if no object is supplied.
    if (domainObject == null) return null
    
    // Get the ACL Object identity.
    ObjectIdentity object_identity = objectIdentityRetrievalStrategy.getObjectIdentity(domainObject)
    
    // Lookup all identities (only 1 in our case).
    Map<ObjectIdentity, Acl> result = aclLookupStrategy.readAclsById([object_identity], null)
    
    // Just return the object from the map. This will be null if not found.
    // Preferable to throwing a NotFoundException.
    return result[object_identity];
  }
  
  private Map<Integer, Permission> definedPerms
  List<Permission> getDefinedPerms () {
    if (!definedPerms) {
      
      definedPerms = [:]
      
      // Retrieve all the defined permissions on the base class.
      def propDefs = GrailsClassUtils.getPropertiesAssignableToType(BasePermission.class, Permission.class)
      
      // Each definition.
      propDefs.each {PropertyDescriptor d ->
        
        Permission p = BasePermission."${d.baseName}"
        
        // Base permission.
        definedPerms[p.mask] = [name: GrailsNameUtils.getNaturalName(d.baseName), inst:p]
      }
    }
    
    // Return the define permission.
    definedPerms
  }
}