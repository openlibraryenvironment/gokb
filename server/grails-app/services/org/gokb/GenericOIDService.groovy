package org.gokb

class GenericOIDService {

  def grailsApplication
  Map classCache = [:]

  public Object resolveOID(oid, boolean lock=false) {
    List oid_components = oid.split(':')
    Object result = null
    Class clazz = classCache[oid_components[0]]

    if ( clazz == null ) {
      def domain_class = grailsApplication.getArtefact('Domain', oid_components[0])

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
        log.debug("Unable to locate instance of ${oid_components[0]} with id ${oid_components[1]}")
    }
    else {
      log.debug("resolve OID failed to identify a domain class. Input was ${oid_components}")
    }
    result
  }

  public Object resolveOID2(oid) {
    List oid_components = oid.split(':')
    Object result = null

    Class clazz = classCache[oid_components[0]]

    if ( clazz == null ) {
      def domain_class = grailsApplication.getArtefact('Domain', oid_components[0])

      if ( domain_class ) {
        clazz = domain_class.getClazz()
        classCache[oid_components[0]] = clazz
      }
    }

    if ( clazz ) {
      if ( oid_components[1] == '__new__' ) {
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

  public Long oidToId(oid) {
    Long result = null

    if (oid) {
      if (oid.contains(':')){
        List oid_components = oid.split(':')

        try {
          result = Long.parseLong(oid_components[1])
        }
        catch (Exception e) {
          log.debug("Unable to resolve oid $oid!")
        }
      }
      else {
        try {
          result = Long.parseLong(oid)
        }
        catch (Exception e) {
          log.debug("Unable to resolve oid $oid!")
        }
      }
    }

    result
  }
}
