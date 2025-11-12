package org.gokb

import org.gokb.cred.*;
import grails.converters.JSON
import groovy.time.TimeCategory

class FwkController {

  def springSecurityService

  @Deprecated
  def history() {
    log.debug("FwkController::history...");
    def result = [:]

    def obj = resolveOID2(params.id)

    if(obj && obj.isReadable()) {

      result.max = params.max ?: 20;
      result.offset = params.offset ?: 0;
      result.timestamp = new Date()
      result.objectclass = obj.getClass().getSimpleName()
      result.label = obj.name ?: obj.id
      result.historyLines = []
    }else{
      log.error("resolve OID failed to identify a domain class. Input was ${params.id}")
    }
    withFormat {
      html { result }
      json { render result as JSON }
    }
  }

  def notes() {
    log.debug("FwkController::notes...");
    def result = [:]
    result.user = User.get(springSecurityService.principal.id)
    // result.owner =
    def obj = resolveOID2(params.id)

    if ( obj.isReadable() ) {

      def oid_components = params.id.split(':')
      def qry_params = [cls: oid_components[0], owner: Long.parseLong(oid_components[1])]
      result.ownerClass = oid_components[0]
      result.ownerId = oid_components[1]

      result.max = params.max ?: 20
      result.offset = params.offset ?: 0

      result.noteLines = Note.executeQuery("select n from Note as n where ownerClass = :cls and ownerId = :owner order by id desc", qry_params, [max:result.max, offset:result.offset])
      result.noteLinesTotal = Note.executeQuery("select count(n.id) from Note as n where ownerClass = :cls and ownerId = :owner", qry_params)[0]
    }

    result
  }

  def attachments() {
    log.debug("FwkController::attachments...")
  }

  def resolveOID2(oid) {
    def oid_components = oid.split(':')
    def result = null;
    def domain_class=null;
    domain_class = grailsApplication.getArtefact('Domain',oid_components[0])
    if ( domain_class ) {
      if ( oid_components[1]=='__new__' ) {
        result = domain_class.getClazz().refdataCreate(oid_components)
        log.debug("Result of create ${oid} is ${result}")
      }
      else {
        result = domain_class.getClazz().get(oid_components[1])
      }
    }
    else {
      log.error("resolve OID failed to identify a domain class. Input was ${oid_components}")
    }
    result
  }

  def toggleWatch() {
    log.debug("FwkController::toggleWatch(${params})")
    def result = [change:0]

    def displayobj = resolveOID2(params.oid)
    def user = springSecurityService.currentUser

    if ( displayobj && user ) {
      def watch = ComponentWatch.findByComponentAndUser(displayobj, user)
      if (watch) {
        // watch exists
        watch.delete(flush:true)
        result.change = -1
      }
      else {
        // Create new watch
        def new_watch = new ComponentWatch(component:displayobj, user:user).save(flush:true, failOnError:true)
        result.change = 1
      }
    }
    render result as JSON
  }

}
