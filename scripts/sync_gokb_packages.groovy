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

          xmltipp.coverage.each { tci ->
            newtipp.coverage.add([startDate: cleanText( tci.'@startDate'.text()),
                                  startVolume: cleanText( tci.'@startVolume'.text()),
                                  startIssue: cleanText( tci.'@startIssue'.text()),
                                  endDate: cleanText( tci.'@endDate'.text()),
                                  endVolume: cleanText( tci.'@endVolume'.text()),
                                  endIssue: cleanText( tci.'@endIssue'.text()),
                                  coverageDepth: cleanText( tci.'@coverageDepth'.text()),
                                  coverageNote: cleanText( tci.'@coverageNote'.text()),
                                  embargo: cleanText( tci.'@embargo'.text() ) ])
          }
          
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
