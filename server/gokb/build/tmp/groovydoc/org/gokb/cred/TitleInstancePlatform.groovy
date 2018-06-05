package org.gokb.cred

import javax.persistence.Transient

class TitleInstancePlatform extends KBComponent {

  String url

  static hasByCombo = [
    tiplHostPlatform        : Platform,
    tiplTitle               : TitleInstance
  ]

  static mappedByCombo = [
    tiplHostPlatform        : 'hostedTipps',
    tiplTitle               : 'tipps',
  ]

  public getPersistentId() {
    "gokb:TIPL:${title?.id}:${hostPlatform?.id}"
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
        def tipl = new TitleInstancePlatform(title:title, hostPlatform:platform, url:url).save()
      }
    }
  }
}
