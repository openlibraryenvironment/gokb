package org.gokb.cred

import javax.persistence.Transient
import groovy.util.logging.*

class TitleInstancePlatform extends KBComponent {

  String url

  static hasByCombo = [
    tiplHostPlatform        : Platform,
    tiplTitle               : TitleInstance
  ]

  static mappedByCombo = [
    tiplHostPlatform        : 'hostedTitles',
    tiplTitle               : 'tipls',
  ]

  public getPersistentId() {
    "gokb:TIPL:${tiplTitle?.id}:${tiplHostPlatform?.id}"
  }

  def availableActions() {
    [ [code:'method::retire', label:'Retire'],
      [code:'method::deleteSoft', label:'Delete', perm:'delete'],
      [code:'method::setActive', label:'Set Current']
    ]
  }

  static constraints = {
    url (nullable:true, blank:true)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  @Override
  public String getNiceName() {
	return "TIPL";
  }

  @Transient
  public static def ensure(title, platform, url) {
    if ( ( title != null ) && ( platform != null ) && ( url?.trim()?.length() > 0 ) ) {
      def status_current = RefdataCategory.lookup('KBComponent.Status', 'Current')
      def r = TitleInstancePlatform.executeQuery('''select tipl
              from TitleInstancePlatform as tipl,
              Combo as titleCombo,
              Combo as platformCombo
              where titleCombo.toComponent=tipl
              and titleCombo.fromComponent=?
              and platformCombo.toComponent=tipl
              and platformCombo.fromComponent=?
              and tipl.status=?
              ''',[title, platform, status_current])

      if ( r.size() == 0 ) {
        def tipl = new TitleInstancePlatform(url:url).save(flush:true, failOnError:true)

        def plt_combo_type = RefdataCategory.lookup('Combo.Type', 'Platform.HostedTitles')
        def plt_combo = new Combo(toComponent:tipl, fromComponent:platform, type:plt_combo_type).save(flush:true, failOnError:true);

        def ti_combo_type = RefdataCategory.lookup('Combo.Type', 'TitleInstance.Tipls')
        def ti_combo = new Combo(toComponent:tipl, fromComponent:title, type:ti_combo_type).save(flush:true, failOnError:true);

        return tipl

      } else if ( r.size() == 1 ) {
        def matched_tipl = r[0]

        if (url && matched_tipl.url != url) {
          matched_tipl.url = url
        }
        return matched_tipl

      } else {
        return null
        log.warn("Found more than one TIPL for ${title} on ${platform}!")
      }
    }
  }
}
