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

  static constraints = {
    url (nullable:true, blank:true)
  }

  @Transient
  def getPermissableCombos() {
    [
    ]
  }

  public String getNiceName() {
	return "TIPL";
  }

  @Transient
  public static ensure(title, platform, url) {
    if ( ( title != null ) && ( platform != null ) ) {
      def r = TitleInstancePlatform.executeQuery('''select tipl
              from TitleInstancePlatform as tipl,
              Combo as titleCombo,
              Combo as platformCombo
              where titleCombo.fromComponent=tipl
              and titleCombo.toComponent=?
              and platformCombo.fromComponent=tipl
              and platformCombo.toComponent=?
              ''',[title, platform])

      if ( r.size() == 0 ) {
        def tipl = new TitleInstancePlatform(url:url).save()

        def combo_status_active = RefdataCategory.lookupOrCreate(Combo.RD_STATUS, Combo.STATUS_ACTIVE)

        def plt_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'Platform.HostedTitles')
        def plt_combo = new Combo(toComponent:tipl, fromComponent:platform, type:plt_combo_type, status:combo_status_active).save(flush:true, failOnError:true);

        def ti_combo_type = RefdataCategory.lookupOrCreate('Combo.Type', 'TitleInstance.Tipls')
        def ti_combo = new Combo(toComponent:tipl, fromComponent:title, type:ti_combo_type, status:combo_status_active).save(flush:true, failOnError:true);

      } else if ( r.size() == 1 ) {
        def matched_tipl = r[0]

        if (url && matched_tipl.url != url) {
          matched_tipl.url = url
        }
      } else {
        log.warn("Found more than one TIPL for ${title.name} on ${platform.name}!")
      }
    }
  }
}
