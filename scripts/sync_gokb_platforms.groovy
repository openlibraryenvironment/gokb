#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase


while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/oai/platforms") { resp, body ->

    body?.'ListRecords'?.'record'.eachWithIndex { rec, index ->

      def data = rec.metadata.gokb.platform
      def directFields = ['authentication', 'software', 'service']

      println("Record ${index + 1}")

      def resourceFieldMap = addCoreItems ( data )
      
      resourceFieldMap['platformName'] = cleanText(data.name.text())
      resourceFieldMap['platformUrl'] = cleanText(data.primaryUrl.text())

      if (data.provider?.name) {
        resourceFieldMap['provider'] = addCoreItems ( data.provider )
      }
      else {
        directFields.add('provider')
      }
      
      directAddFields (data, directFields, resourceFieldMap)
      
      resources.add(resourceFieldMap)

      config.lastTimestamp = rec.header.datestamp.text()
    }
  }
  
  resources.each {
    sendToTarget (path: "${targetContext}/integration/crossReferencePlatform", body: it)
  }
  
  // Save the config.
  saveConfig()
}

setLastRun ()
saveConfig ()

println("Total: ${total}, Errors: ${errors}")
