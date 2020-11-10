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
  def ESWrapperService
  def ESSearchService
  def dateFormatService
  def sessionFactory

  public static String TIPPS_QRY = 'from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=? and c.toComponent=tipp and c.type = ? and tipp.status = ?';



  def packageContent() {
    log.debug("packageContent::${params}")
    def result = [:]
    if ( params.id ) {
      def pkg_id_components = params.id.split(':');
      
      if ( pkg_id_components?.size() == 2 ) {
        result.pkg = Package.get(Long.parseLong(pkg_id_components[1]));
      }
      else {
        result.pkg = Package.findByUuid(params.id)
      }
      
      if (result.pkg) {
        def tipp_combo_rdv = RefdataCategory.lookupOrCreate('Combo.Type','Package.Tipps')
        def status_current = RefdataCategory.lookupOrCreate('KBComponent.Status','Current')
        
        result.pkgId = result.pkg.id
        result.pkgName = result.pkg.name
        log.debug("Tipp qry name: ${result.pkgName}");
        
        result.titleCount = TitleInstancePackagePlatform.executeQuery('select count(tipp.id) '+TIPPS_QRY,[result.pkgId, tipp_combo_rdv, status_current])[0]
        result.tipps = TitleInstancePackagePlatform.executeQuery('select tipp '+TIPPS_QRY+' order by tipp.id',[result.pkgId, tipp_combo_rdv, status_current],[offset:params.offset?params.long('offset'):0,max:10])
        log.debug("Tipp qry done ${result.tipps?.size()}");
      }
    }
    result
  }


  def index() {
    log.debug("PublicController::index ${params}");
    def result = [:]

    def mutableParams = new HashMap(params)

    if ( mutableParams.max == null )
      mutableParams.max = 10
    else
      mutableParams.max = Integer.parseInt(mutableParams.max)

    mutableParams.componentType = "Package" // Tells ESSearchService what to look for

    if ( mutableParams.offset == null )
      mutableParams.offset = 0
    else
      mutableParams.offset = Integer.parseInt(mutableParams.offset)

    if( ( mutableParams.q == null ) || (mutableParams.q == '') )  
      mutableParams.q = '*'
    // params.remove('q');
    // params.isPublic="Yes"

    if(mutableParams.lastUpdated){
      mutableParams.lastModified ="[${params.lastUpdated} TO 2100]"
    }
    if (!mutableParams.sort){
      mutableParams.sort='sortname'
      mutableParams.order = 'asc'
    }
    if(mutableParams.search.equals('yes')){
      //when searching make sure results start from first page
      mutableParams.offset = 0
      mutableParams.search = null
    }
    if(mutableParams.filter == 'current')
      mutableParams.tempFQ = ' -pkg_scope:\"Master File\" -\"open access\" ';

    result =  ESSearchService.search(mutableParams)
    result.transforms = grailsApplication.config.packageTransforms

    result
  }


  // @Transactional(readOnly = true)
  def kbart() {

    def pkg = genericOIDService.resolveOID(params.id)

    def export_date = dateFormatService.formatDate(new Date());

    def filename = "GOKb Export : ${pkg.name} : ${export_date}.tsv"

    try {
      response.setContentType('text/tab-separated-values');
      response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
      response.contentType = "text/tab-separated-values" // "text/tsv"

      def out = response.outputStream
      out.withWriter { writer ->

        def sanitize = { it ? "${it}".trim() : "" }

          // As per spec header at top of file / section
          // II: Need to add in preceding_publication_title_id
          writer.write('publication_title\t'+
                       'print_identifier\t'+
                       'online_identifier\t'+
                       'date_first_issue_online\t'+
                       'num_first_vol_online\t'+
                       'num_first_issue_online\t'+
                       'date_last_issue_online\t'+
                       'num_last_vol_online\t'+
                       'num_last_issue_online\t'+
                       'title_url\t'+
                       'first_author\t'+
                       'title_id\t'+
                       'embargo_info\t'+
                       'coverage_depth\t'+
                       'coverage_notes\t'+
                       'publisher_name\t'+
                       'preceding_publication_title_id\t'+
                       'date_monograph_published_print\t'+
                       'date_monograph_published_online\t'+
                       'monograph_volume\t'+
                       'monograph_edition\t'+
                       'first_editor\t'+
                       'parent_publication_title_id\t'+
                       'publication_type\t'+
                       'access_type\n');

          // scroll(ScrollMode.FORWARD_ONLY)
          def session = sessionFactory.getCurrentSession()
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def combo_pkg_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status <> :sd and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p',pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sd', status_deleted)
          query.setParameter('ct', combo_pkg_tipps)


          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {
            def tipp_id = tipps.get(0);

              TitleInstancePackagePlatform.withNewSession {
                def tipp = TitleInstancePackagePlatform.get(tipp_id)
                writer.write(
                            sanitize( tipp.title.name ) + '\t' +
                            sanitize( tipp.title.getIdentifierValue('ISSN') ) + '\t' +
                            sanitize( tipp.title.getIdentifierValue('eISSN') ) + '\t' +
                            sanitize( tipp.startDate ) + '\t' +
                            sanitize( tipp.startVolume ) + '\t' +
                            sanitize( tipp.startIssue ) + '\t' +
                            sanitize( tipp.endDate ) + '\t' +
                            sanitize( tipp.endVolume ) + '\t' +
                            sanitize( tipp.endIssue ) + '\t' +
                            sanitize( tipp.url ) + '\t' +
                            '\t'+  // First Author
                            sanitize( tipp.title.getId() ) + '\t' +
                            sanitize( tipp.embargo ) + '\t' +
                            sanitize( tipp.coverageDepth ) + '\t' +
                            sanitize( tipp.coverageNote ) + '\t' +
                            sanitize( tipp.title.getCurrentPublisher()?.name ) + '\t' +
                            sanitize( tipp.title.getPrecedingTitleId() ) + '\t' +
                            '\t' +  // date_monograph_published_print
                            '\t' +  // date_monograph_published_online
                            '\t' +  // monograph_volume
                            '\t' +  // monograph_edition
                            '\t' +  // first_editor
                            '\t' +  // parent_publication_title_id
                            sanitize( tipp.title?.medium?.value ) + '\t' +  // publication_type
                            sanitize( tipp.paymentType?.value ) +  // access_type
                            '\n');
                tipp.discard();
              }
          }

          tipps.close()

          writer.flush();
          writer.close();
        }
      out.close()
    }
    catch ( Exception e ) {
      log.error("Problem with export",e);
    }
  }

  def packageTSVExport() {

    def export_date = dateFormatService.formatDate(new Date());

    def pkg = genericOIDService.resolveOID(params.id)

    if ( pkg == null )
      return;

    def filename = "GoKBPackage-${params.id}.tsv";

    try {
      response.setContentType('text/tab-separated-values');
      response.setHeader("Content-disposition", "attachment; filename=\"${filename}\"")
      response.contentType = "text/tab-separated-values" // "text/tsv"

      def out = response.outputStream
      out.withWriter { writer ->

        def sanitize = { it ? "${it}".trim() : "" }




          // As per spec header at top of file / section
          writer.write("GOKb Export : ${pkg.provider?.name} : ${pkg.name} : ${export_date}\n");

          writer.write('TIPP ID	TIPP URL	Title ID	Title	TIPP Status	[TI] Publisher	[TI] Imprint	[TI] Published From	[TI] Published to	[TI] Medium	[TI] OA Status	'+
                     '[TI] Continuing series	[TI] ISSN	[TI] EISSN	Package	Package ID	Package URL	Platform	'+
                     'Platform URL	Platform ID	Reference	Edit Status	Access Start Date	Access End Date	Coverage Start Date	'+
                     'Coverage Start Volume	Coverage Start Issue	Coverage End Date	Coverage End Volume	Coverage End Issue	'+
                     'Embargo	Coverage note	Host Platform URL	Format	Payment Type\n');

          def session = sessionFactory.getCurrentSession()
          def status_deleted = RefdataCategory.lookup('KBComponent.Status', 'Deleted')
          def combo_pkg_tipps = RefdataCategory.lookup('Combo.Type', 'Package.Tipps')
          def query = session.createQuery("select tipp.id from TitleInstancePackagePlatform as tipp, Combo as c where c.fromComponent.id=:p and c.toComponent=tipp  and tipp.status <> :sd and c.type = :ct order by tipp.id")
          query.setReadOnly(true)
          query.setParameter('p',pkg.getId(), StandardBasicTypes.LONG)
          query.setParameter('sd', status_deleted)
          query.setParameter('ct', combo_pkg_tipps)

          ScrollableResults tipps = query.scroll(ScrollMode.FORWARD_ONLY)

          while (tipps.next()) {

            def tipp_id = tipps.get(0);

            TitleInstancePackagePlatform tipp = TitleInstancePackagePlatform.get(tipp_id)

            writer.write( sanitize( tipp.getId() ) + '\t' + sanitize( tipp.url ) + '\t' + sanitize( tipp.title.getId() ) + '\t' + sanitize( tipp.title.name ) + '\t' +
                          sanitize( tipp.status.value ) + '\t' + sanitize( tipp.title.getCurrentPublisher()?.name ) + '\t' + sanitize( tipp.title.imprint?.name ) + '\t' + sanitize( tipp.title.publishedFrom ) + '\t' +
                          sanitize( tipp.title.publishedTo ) + '\t' + sanitize( tipp.title.medium?.value ) + '\t' + sanitize( tipp.title.oa?.status ) + '\t' +
                          sanitize( tipp.title.continuingSeries?.value ) + '\t' +
                          sanitize( tipp.title.getIdentifierValue('ISSN') ) + '\t' +
                          sanitize( tipp.title.getIdentifierValue('eISSN') ) + '\t' +
                          sanitize( pkg.name ) + '\t' + sanitize( pkg.getId() ) + '\t' + '\t' + sanitize( tipp.hostPlatform.name ) + '\t' +
                          sanitize( tipp.hostPlatform.primaryUrl ) + '\t' + sanitize( tipp.hostPlatform.getId() ) + '\t\t' + sanitize( tipp.status?.value ) + '\t' + sanitize( tipp.accessStartDate )  + '\t' +
                          sanitize( tipp.accessEndDate ) + '\t' + sanitize( tipp.startDate ) + '\t' + sanitize( tipp.startVolume ) + '\t' + sanitize( tipp.startIssue ) + '\t' + sanitize( tipp.endDate ) + '\t' +
                          sanitize( tipp.endVolume ) + '\t' + sanitize( tipp.endIssue ) + '\t' + sanitize( tipp.embargo ) + '\t' + sanitize( tipp.coverageNote ) + '\t' + sanitize( tipp.hostPlatform.primaryUrl ) + '\t' +
                          sanitize( tipp.format?.value ) + '\t' + sanitize( tipp.paymentType?.value ) +
                          '\n');
            tipp.discard();
          }
        }

        writer.flush();
        writer.close();
      out.close()
    }
    catch ( Exception e ) {
      log.error("Problem with export",e);
    }
  }
}
