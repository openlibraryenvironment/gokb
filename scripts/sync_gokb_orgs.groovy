#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase

//println("Pulling latest messages");
//pullLatest(config, httpbuilder, cfg_file)
//println("All done");

// Dry run first until we are sorted!
setDryRun (true)

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '/gokb/oai/orgs') { resp, body ->

    body?.'ListRecords'?.'record'.eachWithIndex { r, index ->

      println("Record ${index + 1}")
      def data = r.metadata.gokb.org

      def resourceFieldMap = addCoreItems ( data )
      resourceFieldMap['homepage'] = cleanText(data.homepage?.text())
      resourceFieldMap['mission'] = cleanText(data.mission?.text())
//        resourceFieldMap['customIdentifiers'] = []
//        resourceFieldMap['variantNames'] = []
      
      resources.add(resourceFieldMap)
    }
  }
  
  resources.each {
    sendToTarget (path: '/gokb/oai/orgs', body: it) 
  }
}
