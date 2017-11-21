package org.gokb

import grails.util.GrailsNameUtils

import java.lang.reflect.Field
import java.lang.reflect.Modifier

import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.ObjectIdentity
import org.springframework.security.acls.model.Permission


class GokbAclService {
  
  /** Ensure we set as none transactional **/
  // static transactional = false
  
  def aclLookupStrategy
  def objectIdentityRetrievalStrategy
  
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
  
  private static Map<Integer, Permission> definedPerms = null
  
  Map<Integer, Permission> getDefinedPerms () {

    log.debug("getDefinedPerms");

    if (!GokbAclService.definedPerms) {
      
      log.debug("Creating map of defined perms");
      // Set to a tree map.
      GokbAclService.definedPerms = [:] as TreeMap
      
      // Get all static fields that are of the type Permission
      BasePermission.class.declaredFields.each { Field f ->

        
        if (Modifier.isStatic(f.getModifiers()) && Permission.class.isAssignableFrom(f.getType())) {
          

          // Get the static Permission.
          Permission p = BasePermission."${f.getName()}"

          log.debug("Adding mask : ${p.mask} with name ${f.getName()}");
          
          // Base permission.
          GokbAclService.definedPerms[p.mask] = [name: GrailsNameUtils.getNaturalName(f.getName()), inst:p]
        }
      }
    }
    
    // Return the define permission.
    GokbAclService.definedPerms
  }
}
