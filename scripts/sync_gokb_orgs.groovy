#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase


while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/oai/orgs") { resp, body ->

    body?.'ListRecords'?.'record'.eachWithIndex { rec, index ->

      println("Record ${index + 1}")
      def data = rec.metadata.gokb.org

      def resourceFieldMap = addCoreItems ( data )
      directAddFields (data, ['homepage', 'mission'], resourceFieldMap)
      
      resources.add(resourceFieldMap)
      config.lastTimestamp = rec.header.datestamp.text()
    }
  }
  
  resources.each {
    sendToTarget (path: "${targetContext}/integration/assertOrg", body: it)
  }
  
  // Save the config.
  saveConfig()
}

setLastRun ()
saveConfig ()

println("Total: ${total}, Errors: ${errors}")
