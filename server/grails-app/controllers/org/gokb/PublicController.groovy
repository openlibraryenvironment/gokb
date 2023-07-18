package org.gokb

import grails.converters.*
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.access.annotation.Secured;
import org.gokb.cred.*
import org.springframework.web.multipart.MultipartHttpServletRequest
import com.k_int.ConcurrencyManagerService;
import com.k_int.ConcurrencyManagerService.Job
import java.security.MessageDigest
import grails.converters.JSON

import org.hibernate.ScrollMode
import org.hibernate.ScrollableResults
import org.hibernate.type.*
import org.hibernate.Hibernate
import org.hibernate.type.StandardBasicTypes



class PublicController {

  def genericOIDService
  def springSecurityService
  def concurrencyManagerService
  def TSVIngestionService
  def PackageService
  def ESWrapperService
  def ESSearchService
  def dateFormatService
  def sessionFactory

  public static String TIPPS_QRY = 'from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id = :pkg and c.toComponent = tipp and c.type = :ct and tipp.status = :cs'

  def packageContent() {
    log.debug("packageContent::${params}")
    def result = [:]

    if ( params.id ) {
      def pkg_id_components = params.id.split(':')

      if ( pkg_id_components?.size() == 2 ) {
        try {
          result.pkg = Package.get(Long.parseLong(pkg_id_components[1]))
        }
        catch (Exception e) {
          result.result = 'ERROR'
          result.message = "Unable to resolve id parameter!"
        }
      }
      else {
        result.pkg = Package.findByUuid(params.id)
      }

      if (result.pkg) {
        def tipp_combo_rdv = RefdataCategory.lookupOrCreate('Combo.Type','Package.Tipps')
        def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')

        result.pkgId = result.pkg.id
        result.pkgName = result.pkg.name
        log.debug("Tipp qry name: ${result.pkgName}")
        def offset = params.offset ? params.int('offset') : 0

        result.titleCount = TitleInstancePackagePlatform.executeQuery('select count(tipp.id) '+TIPPS_QRY, [pkg: result.pkgId, ct: tipp_combo_rdv, cs: status_current])[0]
        result.tipps = []

        def tipps = TitleInstancePackagePlatform.executeQuery('select tipp '+TIPPS_QRY+' order by tipp.id', [pkg: result.pkgId, ct: tipp_combo_rdv, cs: status_current], [offset: offset, max:10, readOnly: true])

        tipps.each { t ->
          Map tobj = [
            name: t.name,
            coverageDepth: t.coverageDepth?.value ?: null,
            ids: []
          ]

          t.ids.each { i ->
            def ido = Identifier.get(i.id)

            tobj.ids << [value: ido.value, namespace: IdentifierNamespace.get(ido.namespace.id).value]
          }

          result.tipps << tobj
        }

        log.debug("Tipp qry done ${result.tipps?.size()}")
      }
    }
    result
  }


  def index() {
    log.debug("PublicController::index ${params}")
    def result = [:]

    def mutableParams = new HashMap(params)

    if (mutableParams.max) {
      try {
        mutableParams.max = Integer.parseInt(mutableParams.max)
      }
      catch (Exception e ) {
        result.result = 'ERROR'
        result.message = "Unable to parse parameter value 'max'!"
      }
    }
    else
      mutableParams.max = 10

    mutableParams.componentType = "Package" // Tells ESSearchService what to look for

    if (mutableParams.offset) {
      try {
        mutableParams.offset = Integer.parseInt(mutableParams.offset)
      }
      catch (Exception e ) {
        result.result = 'ERROR'
        result.message = "Unable to parse parameter value 'offset'!"
      }
    }
    else {
      mutableParams.offset = 0
    }

    if ((mutableParams.q == null ) || (mutableParams.q == ''))
      mutableParams.q = '*'
    // params.remove('q');
    // params.isPublic="Yes"

    if (mutableParams.lastUpdated) {
      mutableParams.lastModified = "[${params.lastUpdated} TO 2100]"
    }

    if (!mutableParams.sort){
      mutableParams.sort = 'sortname'
      mutableParams.order = 'asc'
    }

    if (mutableParams.search.equals('yes')) {
      //when searching make sure results start from first page
      mutableParams.offset = 0
      mutableParams.search = null
    }

    if (mutableParams.filter == 'current')
      mutableParams.tempFQ = ' -pkg_scope:\"Master File\" -\"open access\" '

    if (result.result != 'ERROR') {
      result = ESSearchService.search(mutableParams)
      result.transforms = grailsApplication.config.packageTransforms
    }

    result
  }


  // @Transactional(readOnly = true)
  def kbart() {
    def type = params.exportType == 'title' ? PackageService.ExportType.KBART_TITLE : PackageService.ExportType.KBART_TIPP
    def pkg = Package.findByUuid(params.id) ?: (genericOIDService.oidToId(params.id) ? Package.get(genericOIDService.oidToId(params.id)) : null)

    def export_date = dateFormatService.formatDate(new Date())

    if (pkg) {
      packageService.sendFile(pkg, type, response)
    }
    else {
      log.debug("Unable to resolve package by ID ${params.id}!")
      response.status = 404
    }
  }

  def packageTSVExport() {
    def export_date = dateFormatService.formatDate(new Date())

    def pkg = genericOIDService.resolveOID(params.id)

    if (pkg) {
      packageService.sendFile(pkg, PackageService.ExportType.TSV, response)
    }
    else {
      log.debug("Unable to resolve package by ID ${params.id}!")
      response.status = 404
    }
  }
}
