package gokb

import grails.transaction.Transactional
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.ObjectIdentity


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
}
