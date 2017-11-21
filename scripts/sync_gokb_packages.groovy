#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase



boolean includeTipps = true
while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '/gokb/oai/packages') { resp, body ->

    body?.'ListRecords'?.'record'.metadata.gokb.package.eachWithIndex { data, index ->

      println("Record ${index + 1}")
      def resourceFieldMap = [
        packageHeader : directAddFields (data, ['scope', 'listStatus', 'breakable' ,'consistent', 'fixed', 'paymentType',
        'global', 'listVerifier', 'userListVerifier', 'nominalPlatform', 'nominalProvider', 'listVerifiedDate'], addCoreItems ( data ) )
      ]
      
      // TIPPs
      resourceFieldMap.tipps = []

      if (includeTipps) {
        data.TIPPs.TIPP.each { xmltipp ->
          
          // TIPP.
          def newtipp = directAddFields (data, ['medium', 'url'], addCoreItems ( xmltipp ))
          newtipp.accessStart = cleanText( xmltipp.access.'@start'.text() )
          newtipp.accessEnd = cleanText( xmltipp.access.'@end'.text() )
          
          // Coverage
          newtipp.coverage = []
          newtipp.coverage.add([startDate: cleanText( xmltipp.coverage.'@startDate'.text()),
                                startVolume: cleanText( xmltipp.coverage.'@startVolume'.text()),
                                startIssue: cleanText( xmltipp.coverage.'@startIssue'.text()),
                                endDate: cleanText( xmltipp.coverage.'@endDate'.text()),
                                endVolume: cleanText( xmltipp.coverage.'@endVolume'.text()),
                                endIssue: cleanText( xmltipp.coverage.'@endIssue'.text()),
                                coverageDepth: cleanText( xmltipp.coverage.'@coverageDepth'.text()),
                                coverageNote: cleanText( xmltipp.coverage.'@coverageNote'.text()),
                                embargo: cleanText( xmltipp.coverage.'@embargo'.text() ) ])
          
          // Title.
          newtipp.title = addCoreItems ( xmltipp.title )
  
          newtipp.platform = directAddFields (xmltipp.platform, ['primaryUrl'], addCoreItems ( xmltipp.platform ))
  
          resourceFieldMap['tipps'].add(newtipp);
        }
      }
      
      resources.add(resourceFieldMap)
    }
  }
  
  resources.each {
    sendToTarget (path: '/gokb/integration/crossReferencePackage', body: it)
  }
  
  Thread.sleep(1000)
  
  // Save the config.
  saveConfig()
}
