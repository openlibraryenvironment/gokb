package org.gokb

import org.springframework.security.access.annotation.Secured;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.io.IOUtils
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.gokb.cred.*
import grails.converters.JSON

class ResourceController {

  def genericOIDService
  def classExaminationService
  def springSecurityService
  def gokbAclService
  def aclUtilService
  def displayTemplateService

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def index() {
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def show() {

    User user = springSecurityService.currentUser

    log.debug("ResourceController::show ${params}");
    def result = ['params':params]
    def oid = params.id
    def displayobj = null
    def read_perm = false

    if (params.type && params.id) {
      oid = "org.gokb.cred." + params.type + ":" + params.id
    }

    if ( oid ) {
      displayobj = KBComponent.findByUuid(oid)

      if (!displayobj) {
        displayobj = genericOIDService.resolveOID(oid)
      }
      else {
        oid = "${displayobj?.class?.name}:${displayobj?.id}"
      }
      
      if ( displayobj ) {

        read_perm = displayobj.isTypeReadable()

        if (read_perm) {

          // Need to figure out whether the current user has curatorial rights (or is an admin).
          // Defaults to true as not all components have curatorial groups defined.

          def curatedObj = displayobj.respondsTo("getCuratoryGroups") ? displayobj : ( displayobj.hasProperty('pkg') ? displayobj.pkg : false )

          if (curatedObj && curatedObj.curatoryGroups && curatedObj.niceName != 'User') {

            def cur = user.curatoryGroups?.id.intersect(curatedObj.curatoryGroups?.id) ?: []
            request.curator = cur
          } else {
            request.curator = null
          }

          def new_history_entry = new History(controller:params.controller,
          action:params.action,
          actionid:oid,
          owner:user,
          title:"View ${displayobj.toString()}").save()

          result.displayobjclassname = displayobj.class.name
          result.__oid = "${result.displayobjclassname}:${displayobj.id}"
  
          log.debug("Looking up display template for ${result.displayobjclassname}");

          result.displaytemplate = displayTemplateService.getTemplateInfo(result.displayobjclassname);

          log.debug("Using displaytemplate: ${result.displaytemplate}");

          // Add any refdata property names for this class to the result.
          result.refdata_properties = classExaminationService.getRefdataPropertyNames(result.displayobjclassname)
          result.displayobjclassname_short = displayobj.class.simpleName

          result.isComponent = (displayobj instanceof KBComponent)
          result.acl = gokbAclService.readAclSilently(displayobj)

          def oid_components = oid.split(':');
          def qry_params = [result.displayobjclassname,Long.parseLong(oid_components[1])];
          result.ownerClass = oid_components[0]
          result.ownerId = oid_components[1]
          result.num_notes = KBComponent.executeQuery("select count(n.id) from Note as n where ownerClass=? and ownerId=?",qry_params)[0];
          // How many people are watching this object
          result.num_watch = KBComponent.executeQuery("select count(n.id) from ComponentWatch as n where n.component=?",displayobj)[0];
          result.user_watching = KBComponent.executeQuery("select count(n.id) from ComponentWatch as n where n.component=? and n.user=?",[displayobj, user])[0] == 1 ? true : false;
        }
        else {
          response.setStatus(403)
          result.status = 403
        }
      }
      else {
        log.debug("unable to resolve object")
        result.status = 404
      }
    }

    withFormat {
      html {
        if (displayobj && read_perm) {
          result.displayobj = displayobj
        }
        result
      }
      json {
        if (displayobj && read_perm) {
          if (result.isComponent) {

            result.resource = displayobj.getAllPropertiesWithLinks(params.withCombos ? true : false)

            result.resource.combo_props = displayobj.allComboPropertyNames
          }
          else if (displayobj.class.name == 'org.gokb.cred.User'){

            result.resource = ['id': displayobj.id, 'username': displayobj.username, 'displayName': displayobj.displayName, 'curatoryGroups': displayobj.curatoryGroups]
          }
          else {
            result.resource = displayobj
          }
        }
        render result as JSON
      }
    }
  }

  @Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
  def download () {
    // Download data stored in a single file tar.gz archive blob field for a particular resource.
    if ( params.id ) {
      def obj = genericOIDService.resolveOID(params.id)

      if ( obj ) {

        if (params.prop && obj."${params.prop}" && obj."${params.prop}" instanceof byte[]) {
          untarSingleFileAndSend (obj."${params.prop}")
          
        } else {
          log.debug("unable to get field data")
        }
      } else {
        log.debug("unable to resolve object")
      }
    }
  }
  
  
  private untarSingleFileAndSend (byte[] content) {
    
    // Input stream for the file.
    ByteArrayInputStream bin = new ByteArrayInputStream(content)
    
    GzipCompressorInputStream gzIn = new GzipCompressorInputStream(bin)
    TarArchiveInputStream tin = new TarArchiveInputStream(gzIn)
    
    // We are assuming only one entry so just access the first and close.
    TarArchiveEntry ae = tin.getNextTarEntry()
    if (ae) {
      
      // Use tika for the content media type.
      Tika t = new Tika()
      String type = null
      
      String filename = ae.name
      int bytes_to_read = ae.getSize()
      
      // If it's less than buffer size then just set it to that.
      long buf_size = bytes_to_read > 4096 ? 4096 : bytes_to_read
      
      byte[] buffer = new byte[buf_size]
      while (bytes_to_read) {
        bytes_to_read -= tin.read(buffer)
        
        // First 4096 bytes should (hopefully) be enough to determine the type....
        if (!type) {
          Metadata m = new Metadata()
          m.add(Metadata.RESOURCE_NAME_KEY, filename)
          type = t.detect(new ByteArrayInputStream(buffer), m)
        }
        
        // Let's return the data.
        response.contentType = "${type}"
        response.setHeader 'Content-disposition', "attachment; filename=\"${filename}\""
        response.outputStream << buffer
      }
    }
    
    // Close the streams.
    IOUtils.closeQuietly(tin)
    IOUtils.closeQuietly(gzIn)
    IOUtils.closeQuietly(bin)
  }
}
