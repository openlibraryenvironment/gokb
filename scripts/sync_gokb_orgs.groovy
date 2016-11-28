#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase


while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '/gokb/oai/orgs') { resp, body ->

    body?.'ListRecords'?.'record'.eachWithIndex { r, index ->

      println("Record ${index + 1}")
      def data = r.metadata.gokb.org

      def resourceFieldMap = addCoreItems ( data )
      directAddFields (data, ['homepage', 'mission'], resourceFieldMap)
      
      resources.add(resourceFieldMap)
    }
  }
  
  resources.each {
    sendToTarget (path: '/gokb/integration/assertOrg', body: it) 
  }
  
  // Save the config.
  saveConfig()
}
