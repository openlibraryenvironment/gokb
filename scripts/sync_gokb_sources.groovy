#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase


while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/oai/sources") { resp, body ->

    body?.'ListRecords'?.'record'.metadata.gokb.eachWithIndex { data, index ->

      println("Record ${index + 1}")

      def resourceFieldMap = addCoreItems ( data )
      directAddFields (data, ['url', 'defaultAccessURL', 'explanationAtSource', 'contextualNotes', 
        'frequency', 'ruleset', 'defaultSupplyMethod', 'defaultDataFormat'], resourceFieldMap)
      
      resources.add(resourceFieldMap)
    }
  }
  
  resources.each {
    sendToTarget (path: "${sourceContext}/integration/assertSource", body: it)
  }
  
  // Save the config.
  saveConfig()
}
