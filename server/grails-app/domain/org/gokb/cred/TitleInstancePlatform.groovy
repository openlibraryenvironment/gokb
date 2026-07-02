package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*

class TitleInstancePlatform extends KBComponent {

  String url
  Platform hostPlatform
  TitleInstance title

  static mapping = [
    includes KBComponent.mapping
    hostPlatform column: 'tipl_host_platform_fk'
    title column: 'tipl_title_fk'
  ]

  static constraints = {
    url (nullable:true, blank:true)
    hostPlatform (nullable: false)
    title (nullable: false)
  }

  public getPersistentId() {
    "gokb:TIPL:${title?.id}:${hostPlatform?.id}"
  }

  def availableActions() {
    [ [code:'method::retire', label:'Retire'],
      [code:'method::deleteSoft', label:'Delete', perm:'delete'],
      [code:'method::setActive', label:'Set Current']
    ]
  }

  @Override
  public String getNiceName() {
    return "TIPL";
  }

  public static TitleInstancePlatform ensure(title, platform, url) {
    if ( ( title != null ) && ( platform != null ) && ( url?.trim()?.length() > 0 ) ) {
      RefdataValue status_current = RefdataCategory.lookup(KBComponent.RD_STATUS, KBComponent.STATUS_CURRENT)
      List r = TitleInstancePlatform.executeQuery('''select id from TitleInstancePlatform as tipl
                                                      where hostPlatform = :plt
                                                      and title = :ti
                                                      and tipl.status = :sc''',
                                                  [ti: title, plt: platform, sc: status_current])

      if ( r.size() == 0 ) {
        TitleInstancePlatform tipl = new TitleInstancePlatform(url:url, title: title, hostPlatform: platform).save(flush:true, failOnError:true)

        return tipl

      } else if ( r.size() == 1 ) {
        TitleInstancePlatform matched_tipl = TitleInstancePlatform.get(r[0])

        if (url && matched_tipl.url != url) {
          matched_tipl.url = url
          matched_tipl.save(flush: true)
        }

        return matched_tipl

      } else {
        log.warn("Found more than one TIPL for ${title} on ${platform}!")
        return null
      }
    }
  }
}
