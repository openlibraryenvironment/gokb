package org.gokb

class GenericOIDService {

  def grailsApplication
  def classCache = [:]

  def resolveOID(oid, boolean lock=false) {

    def oid_components = oid.split(':');

    def result = null;


    def clazz = null;

    clazz = classCache[oid_components[0]]

    if ( clazz == null ) {
      def domain_class=null;
      domain_class = grailsApplication.getArtefact('Domain',oid_components[0])
      if ( domain_class ) {
        clazz = domain_class.getClazz()
        classCache[oid_components[0]] = clazz
      }
    }

    if ( clazz ) {
      if ( lock ) {
        result = clazz.lock(oid_components[1])
      }
      else {
        result = clazz.get(oid_components[1])
      }

      if ( result == null )
        log.debug("Unable to locate instance of ${oid_components[0]} with id ${oid_components[1]}");
    }
    else {
      log.debug("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }

  def resolveOID2(oid) {
    def oid_components = oid.split(':');
    def result = null;

    def clazz = classCache[oid_components[0]]

    if ( clazz == null ) {
      def domain_class=null;
      domain_class = grailsApplication.getArtefact('Domain',oid_components[0])
      if ( domain_class ) {
        clazz = domain_class.getClazz()
        classCache[oid_components[0]] = clazz
      }
    }

    if ( clazz ) {
      if ( oid_components[1]=='__new__' ) {
        result = clazz.refdataCreate(oid_components)
        log.debug("Result of create ${oid} is ${result}");
      }
      else {
        result = clazz.get(oid_components[1])
      }
    }
    else {
      log.debug("resolve OID failed to identify a domain class. Input was ${oid_components}");
    }
    result
  }

  def oidToId(oid) {
    def result = null

    if (oid) {
      if(oid.contains(':')){
        def oid_components = oid.split(':')
        result = Long.parseLong(oid_components[1])
      }
      else {
        result = Long.parseLong(oid)
      }
    }

    result
  }
}
