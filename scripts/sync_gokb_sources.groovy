#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase

// Custom source host.
setSourceBase('http://localhost:8090/')

// Dry run first until we are sorted!
setDryRun (true)

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '/gokb/oai/sources') { resp, body ->

    body?.'ListRecords'?.'record'.metadata.gokb.eachWithIndex { data, index ->

      println("Record ${index + 1}")

      def resourceFieldMap = addCoreItems ( data )
      directAddFields (data, ['url', 'defaultAccessURL', 'explanationAtSource', 'contextualNotes', 
        'frequency', 'ruleset', 'defaultSupplyMethod', 'defaultDataFormat'], resourceFieldMap)
      
      resources.add(resourceFieldMap)
    }
  }
  
  resources.each {
    sendToTarget (path: '/gokb/integration/assertSource', body: it)
  }
  
  // Save the config.
  saveConfig()
}