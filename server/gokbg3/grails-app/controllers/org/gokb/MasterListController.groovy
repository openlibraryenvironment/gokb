package org.gokb

import org.gokb.cred.*
import grails.converters.*
import org.springframework.security.access.annotation.Secured;

@Secured(['ROLE_USER', 'IS_AUTHENTICATED_FULLY'])
class MasterListController {

  def genericOIDService
  def classExaminationService
  def packageService
  
  def refreshAll() {
    packageService.updateAllMasters((params."incremental" == true))
  }

  def index() { 
    def result = [:]

    // Generate list of cp orgs where a tipp exists for that org as a cp
    result.orgs = Org.executeQuery("select o from Org as o where exists ( select p from Package as p join p.outgoingCombos as ic where ic.toComponent = o and ic.type.value='Package.Provider')");

    result
  }

  def org() { 
    def result = [:]

    Org o = Org.get(params.id)
    
    result.o = o

    // Generate list of cp orgs where a tipp exists for that org as a cp
    // select distinct(tipp_title_combos.fromComponent)
    result.tipps = TitleInstancePackagePlatform.executeQuery('''
       select tipp
       from TitleInstancePackagePlatform as tipp
           join tipp.incomingCombos as tipp_pkg_combos
           join tipp.incomingCombos as tipp_title_combos
           join tipp_pkg_combos.fromComponent as pkg
           join pkg.outgoingCombos as pkg_provider_combos
       where tipp_pkg_combos.type.value='Package.Tipps'
         and tipp_title_combos.type.value='TitleInstance.Tipps'
         and pkg_provider_combos.type.value='Package.Provider'
         and pkg_provider_combos.toComponent = ?''',[o])

    result.org_packages = Package.executeQuery('''
      select p 
      from Package as p
      join p.outgoingCombos as pkg_provider_combos
      where pkg_provider_combos.toComponent = ?
        and pkg_provider_combos.type.value = 'Package.Provider'
''',[o]);

    // result.tipps = TitleInstancePackagePlatform.executeQuery('''
    //   select tipp 
    //   from TitleInstancePackagePlatform as tipp
    //   join tipp.incomingCombos as tipp_pkg_combos
    //   join tipp_pkg_combos.fromComponent as pkg
    //   join pkg.outgoingCombos as pkg_provider_combos
    //   where pkg_provider_combos.toComponent = ?
    //     and pkg_provider_combos.type.value = 'Package.Provider'
    //     and tipp_pkg_combos.type.value = 'Package.Tipps'
// ''',[o]);

    result
  }


}
