package org.gokb

import org.gokb.cred.*;

class AdminController {

  def uploadAnalysisService

  def tidyOrgData() {

    def result = [:]

    result.nonMasterOrgs = Org.executeQuery('''
       select org
       from org.gokb.cred.Org as org
           join org.tags as tag
       where tag.owner.desc = 'Org.Authorized'
         and tag.value = 'N'
    ''');

    result.nonMasterOrgs.each { nmo ->
      log.debug("${nmo.id} ${nmo.parent?.id}")
      def combosToDelete = []
      nmo.incomingCombos.each { ic ->
        combosToDelete.add(ic); //ic.delete(flush:true)
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

    redirect(url: request.getHeader('referer'))
  }

  def reSummariseLicenses() {
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '.'

    DataFile.executeQuery("select d from DataFile as d where d.doctype=?",['http://www.editeur.org/onix-pl:PublicationsLicenseExpression']).each { df ->
      log.debug(df);
      df.incomingCombos.each { ic ->
        log.debug(ic);
        if ( ic.fromComponent instanceof License ) {
          try {
            log.debug("Regenerate license for ${ic.fromComponent.id}");

            def sub1 = df.guid.substring(0,2);
            def sub2 = df.guid.substring(2,4);
            def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${df.guid}";
            def source_file = new File(temp_file_name);
            ic.fromComponent.summaryStatement = uploadAnalysisService.generateSummary(source_file);
            ic.fromComponent.save(flush:true);
            log.debug("Completed regeneration... size is ${ic.fromComponent.summaryStatement?.length()}");
          }
          catch ( Exception e ) {
            log.error("Problem",e);
          }
        }
      }
    }
    redirect(url: request.getHeader('referer'))
  }
}
