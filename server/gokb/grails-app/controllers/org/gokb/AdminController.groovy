package org.gokb

import org.gokb.cred.*
import grails.converters.JSON


class AdminController {

  def uploadAnalysisService
  def FTUpdateService
  def packageService
  def grailsCacheAdminService
  def refineService
  def titleAugmentService
  def concurrencyManagerService

  def tidyOrgData() {

    concurrencyManagerService.createJob {
      def result = [:]

      def publisher_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Publisher');

      result.nonMasterOrgs = Org.executeQuery('''
         select org
         from org.gokb.cred.Org as org
             join org.tags as tag
         where tag.owner.desc = 'Org.Authorized'
           and tag.value = 'N'
      ''');

      result.nonMasterOrgs.each { nmo ->

        if ( nmo.parent != null ) {

            nmo.parent.variantNames.add(new KBComponentVariantName(variantName:nmo.name, owner:nmo.parent))
            nmo.parent..save();

          log.debug("${nmo.id} ${nmo.parent?.id}")
          def combosToDelete = []
          nmo.incomingCombos.each { ic ->
            combosToDelete.add(ic); //ic.delete(flush:true)

            if ( ic.type == publisher_combo_type ) {
              log.debug("Got a publisher combo");
              if ( nmo.parent != null ) {
                def new_pub_combo = new Combo(fromComponent:ic.fromComponent, toComponent:nmo.parent, type:ic.type, status:ic.status).save();
              }
              else {
                def authorized_rdv = RefdataCategory.lookupOrCreate('Org.Authorized', 'Y')
                log.debug("No parent set.. try and find an authorised org with the appropriate name(${ic.toComponent.name})");
                def authorized_orgs = Org.executeQuery("select distinct o from Org o join o.variantNames as vn where ( o.name = ? or vn.variantName = ?) AND ? in elements(o.tags)", [ic.toComponent.name, ic.toComponent.name, authorized_rdv]);
                if ( authorized_orgs.size() == 1 ) {
                  def ao = authorized_orgs.get(0)
                  log.debug("Create new publisher link to ${ao}");
                  def new_pub_combo = new Combo(fromComponent:ic.fromComponent, toComponent:ao, type:ic.type, status:ic.status).save();
                }
              }
            }
          }
          nmo.outgoingCombos.each { oc ->
            combosToDelete.add(oc); //ic.delete(flush:true)
            // oc.delete(flush:true)
          }

          nmo.incomingCombos.clear();
          nmo.outgoingCombos.clear();

          combosToDelete.each { cd ->
            cd.delete(flush:true)
          }

          nmo.delete(flush:true)
        }
      }
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def logViewer() {
    cache "until_changed"
    def f = new File ("${grailsApplication.config.log_location}")
    return [file: "${f.canonicalPath}"]
  }

  def reSummariseLicenses() {

    concurrencyManagerService.createJob {
      DataFile.executeQuery("select d from DataFile as d where d.doctype=?",['http://www.editeur.org/onix-pl:PublicationsLicenseExpression']).each { df ->
        log.debug(df);
        df.incomingCombos.each { ic ->
          log.debug(ic);
          if ( ic.fromComponent instanceof License ) {
            def source_file
            try {
              log.debug("Regenerate license for ${ic.fromComponent.id}");
              if(df.fileData){
                source_file = copyUploadedFile(df.fileData,df.guid)
                ic.fromComponent.summaryStatement = uploadAnalysisService.generateSummary(source_file);
                ic.fromComponent.save(flush:true);
                log.debug("Completed regeneration... size is ${ic.fromComponent.summaryStatement?.length()}");
              }else{
                log.error("No file data attached to DataFile ${df.guid}")
              }
            }
            catch ( Exception e ) {
              log.error("Problem",e);
            }finally{
              source_file?.delete()
            }
          }
        }
      }
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def copyUploadedFile(inputfile, deposit_token) {

   def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'

    log.debug("copyUploadedFile...");
    def sub1 = deposit_token.substring(0,2);
    def sub2 = deposit_token.substring(2,4);
    validateUploadDir("${baseUploadDir}");
    validateUploadDir("${baseUploadDir}/${sub1}");
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}");
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}";
    def temp_file = new File(temp_file_name)

     OutputStream outStream = null;
     ByteArrayOutputStream byteOutStream = null;
     try {
       outStream = new FileOutputStream(temp_file);
       byteOutStream = new ByteArrayOutputStream();
       // writing bytes in to byte output stream
       byteOutStream.write(inputfile); //data
       byteOutStream.writeTo(outStream);
     } catch (IOException e) {
       e.printStackTrace();
     } finally {
       outStream.close();
     }
    log.debug("Created temp_file ${temp_file.size()}")

    temp_file
  }

  private def validateUploadDir(path) {
    File f = new File(path);
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }

  def updateTextIndexes() {
    log.debug("Call to update indexe");
    concurrencyManagerService.createJob {
      FTUpdateService.updateFTIndexes();
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def resetTextIndexes() {
    log.debug("Call to update indexe")
    concurrencyManagerService.createJob {
      FTUpdateService.clearDownAndInitES()
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def masterListUpdate() {
    log.debug("Force master list update")
    concurrencyManagerService.createJob {
      packageService.updateAllMasters(true)
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def clearBlockCache() {
    // clear the cache used by the blocks tag…
    grailsCacheAdminService.clearBlocksCache()
    redirect(url: request.getHeader('referer'))
  }

  def buildExtension() {

    // Run the task in the background so we can show the logs in this thread without having to wait
    // for the task to finish.
    concurrencyManagerService.createJob {
      refineService.buildExtension()
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def triggerEnrichments() {
    concurrencyManagerService.createJob {
      log.debug("manually trigger enrichment service");
      titleAugmentService.doEnrichment();
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

  def jobs() {
    log.debug("Jobs");
    def result=[:]
    log.debug("Sort");
    result.jobs = concurrencyManagerService.jobs.sort { it.key }
    log.debug("concurrency manager service");
    result.cms = concurrencyManagerService

    log.debug("Render");
    if ( request.format == 'JSON' ) {
      log.debug("JSON Render");
      render result as JSON
    }

    log.debug("Return");
    result
  }

  def housekeeping() {
    log.debug("Housekeeping");
    concurrencyManagerService.createJob {
      try {
        def ctr = 0;
        def start_time = System.currentTimeMillis();
        log.debug("Remove any ISSN identifiers where an eISSN with the same value is also present");
        // Find all identifier occurrences where the component attached also has an issn with the same value.
        // select combo from Combo as combo where combo.toComponent in (select identifier from Identifier as identifier where identifier.ns.ns = 'eissn' )
        //    and exists (
        log.debug("Query");
        def q1 = Identifier.executeQuery('select i1 from Identifier as i1 where i1.namespace.value = :n1 and exists ( select i2 from Identifier as i2 where i2.namespace.value=:n2 and i2.value = i1.value )',
                                         [n1:'issn', n2:'eissn']);
        log.debug("Query complete, elapsed = ${System.currentTimeMillis() - start_time}");
        def id_combo_type = RefdataValue.findByValue('KBComponent.Ids');
        q1.each { issn ->
          log.debug("cleaning up ${issn.namespace.value}:${issn.value}");
          Combo.executeUpdate('delete from Combo c where c.type=:tp and ( c.fromComponent = :f or c.toComponent=:t )',[f:issn, t:issn, tp:id_combo_type]);
          ctr++;
        }
        log.debug("ISSN/eISSN cleanup complete ctr=${ctr}, elapsed = ${System.currentTimeMillis() - start_time}");
      }
      catch ( Exception e ) {
        e.printStackTrace();
      }
    }.startOrQueue()
    render(view: "logViewer", model: logViewer())
  }

}
