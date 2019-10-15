#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase


while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/oai/licenses") { resp, body ->

    body?.'ListRecords'?.'record'.metadata.gokb.eachWithIndex { data, index ->

      println("Record ${index + 1}")

      def resourceFieldMap = addCoreItems ( data )
      directAddFields (data, [
        'url','file','type', 'licensor', 'licensee', 
        'previous', 'successor', 'model', 'summaryStatement'], resourceFieldMap)
      
      resources.add(resourceFieldMap)
    }
  }
  
  resources.each {
    sendToTarget (path: "${targetContext}/integration/crossReferenceLicense", body: it)
  }
  
  // Save the config.
  saveConfig()
}

setLastRun ()
saveConfig ()

println("Total: ${total}, Errors: ${errors}")
