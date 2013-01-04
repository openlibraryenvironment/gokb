package org.gokb

class GenericOIDService {

  def grailsApplication

  def resolveOID(oid) {

    def oid_components = oid.split(':');

    def result = null;

    def domain_class=null;

    domain_class = grailsApplication.getArtefact('Domain',oid_components[0])


    if ( domain_class ) {
      result = domain_class.getClazz().get(oid_components[1])
    }
    else {
      log.error("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }

}
