package org.gokb.cred

import javax.persistence.Transient;

class IngestionProfile extends KBComponent {
  String packageName
  RefdataValue packageType
  String platformUrl
  IdentifierNamespace providerNamespace

  static hasMany = [
    ingestions: ComponentIngestionSource
  ]

  static mappedBy = [
    ingestions:'profile'
  ]

  static hasByCombo = [
    source: Source,
  ]

  static constraints = {
    packageName (nullable:false, blank:false)
    packageType (nullable:false, blank:false)
    platformUrl (nullable:true, blank:false)
  }

  static mapping = {
    includes KBComponent.mapping

    packageName column:'ebdf_packageName'
    packageType column:'ebdf_packageType'
    platformUrl column:'endf_default_platform_url'
  }

  public String getNiceName() {
    return "Ingestion Profile"
  }


    @Transient
    def getMissingTipps() {
      def result=[]
      if (ingestions && ingestions.size()>1) {
        // can't directly sort a hibernate collection, and we only need the components anyway
        def sources = ingestions.collect() {it.component}
        Collections.sort(sources, {a, b -> a.dateCreated <=> b.dateCreated} as Comparator)
        result=sources[sources.size()-2].tipps
        result-=sources.last().tipps
      }
      result
    }

    @Transient
    def getNewTipps() {
      def result=[]
      if (ingestions) {
        // can't directly sort a hibernate collection, and we only need the components anyway
        def sources = ingestions.collect() {it.component}

        Collections.sort(sources, {a, b -> a.dateCreated <=> b.dateCreated} as Comparator)
        result=sources.last().tipps
        if (sources.size()>1) {
            result-=sources[sources.size()-2].tipps
        }
      }
      result
    }

      /**
   *  Override so that we only return EbookDataFiles that are editable on the
   * typedown searches
   */
  @Override
  static def refdataFind(params) {
    def result = [];
    def ql = null;
    ql = IngestionProfile.findAllByNameIlike("${params.q}%",params)
    if ( ql ) {
    ql.each { t ->
      result.add([id:"${t.class.name}:${t.id}",text:"${t.name}"])
    }
    }

    result
  }
}
