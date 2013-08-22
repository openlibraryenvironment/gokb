package org.gokb

import org.gokb.cred.*

class MasterListController {

  def index() { 
    def result = [:]

    // Generate list of cp orgs where a tipp exists for that org as a cp
    result.orgs = Org.executeQuery("select o from Org as o where exists ( select p from Package as p join p.outgoingCombos as ic where ic.toComponent = o and ic.type.value='Package.Provider')");

    result
  }
}
