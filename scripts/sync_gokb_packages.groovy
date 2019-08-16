#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase



boolean includeTipps = true
while ( moredata ) {
  
  def resources = []
  def status = null

  fetchFromSource (path: '/gokb/oai/packages') { resp, body ->

    body?.'ListRecords'?.'record'.metadata.gokb.package.eachWithIndex { data, index ->

      println("Record ${index + 1}")
      def resourceFieldMap = [
        packageHeader : directAddFields (data, ['scope', 'listStatus', 'breakable' ,'consistent', 'fixed', 'paymentType',
        'global', 'listVerifier', 'userListVerifier', 'nominalProvider', 'listVerifiedDate'], addCoreItems ( data ) )
      ]

      resourceFieldMap.packageHeader['nominalPlatform'] = [name: data.nominalPlatform.name.text(),
                                          primaryUrl: data.nominalPlatform.primaryUrl.text(),
                                          uuid: data.nominalPlatform.'@uuid'.text()]
      
      // TIPPs
      resourceFieldMap.tipps = []

      if (data.status != 'Deleted' && includeTipps) {
        data.TIPPs.TIPP.each { xmltipp ->
          
          // TIPP.
          def newtipp = directAddFields (xmltipp, ['medium', 'url'], addCoreItems ( xmltipp ))
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

          def type = cleanText(data.type?.text())

          if (!type || type == 'JournalInstance' || type == 'Serial') {
            newtipp.title.type = 'Serial'
          }
          else if (type == 'DatabaseInstance') {
            newtipp.title.type = 'Database'
          }
          else if (type == 'BookInstance'|| type == 'Monograph') {
            newtipp.title.type = 'Book'
          }
  
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

println("Total: ${total}, Errors: ${errors}")
