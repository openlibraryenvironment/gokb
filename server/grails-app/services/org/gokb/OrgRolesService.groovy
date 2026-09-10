package org.gokb

import org.gokb.cred.*

class OrgRolesService {

  public Map addMissingRoles() {
    Map result = [new_providers: 0, new_publishers: 0]
    RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
    RefdataValue rdv_publisher = RefdataCategory.lookup('Org.Role', 'Publisher')
    RefdataValue rdv_platform_provider = RefdataCategory.lookup('Org.Role', 'Platform Provider')

    String qry_provider_string = '''from Org as o
                                  where status = :sc
                                  and exists (
                                    select 1 from Platform
                                    where provider = o
                                  )
                                  and :rp not member of o.roles'''

    List missing_provider_orgs = Org.executeQuery(qry_string, [sc: status_current rp: rdv_platform_provider])

    missing_provider_orgs.each { org ->
      org.addToRoles(rdv_platform_provider)
      org.lastSeen = System.currentTimeMillis()
      org.save(flush: true)
    }

    result.new_providers = missing_provider_orgs.size()

    String qry_publisher_string = '''from Org as o
                                  where status = :sc
                                  and exists (
                                    select 1 from TitlePublisher
                                    where publisher = o
                                  )
                                  and :rp not member of o.roles'''

    List missing_publisher_orgs = Org.executeQuery(qry_string, [sc: status_current rp: rdv_publisher])

    missing_publisher_orgs.each { org ->
      org.addToRoles(rdv_publisher)
      org.lastSeen = System.currentTimeMillis()
      org.save(flush: true)
    }

    result.new_publishers = missing_publisher_orgs.size()

    return result
  }
}