package org.gokb

import org.gokb.cred.*;

class AdminController {

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

  reSummariseLicenses() {
    redirect(url: request.getHeader('referer'))
  }
}
