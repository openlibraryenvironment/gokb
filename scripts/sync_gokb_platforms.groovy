#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase


while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/oai/platforms") { resp, body ->

    body?.'ListRecords'?.'record'.eachWithIndex { rec, index ->

      def data = rec.metadata.gokb.platform

      println("Record ${index + 1}")

      def resourceFieldMap = addCoreItems ( data )
      
      resourceFieldMap['platformName'] = cleanText(data.name.text())
      resourceFieldMap['platformUrl'] = cleanText(data.primaryUrl.text())
      
      
      directAddFields (data, ['authentication', 'software', 'service', 'provider'], resourceFieldMap)
      
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

config.lastRun = config.lastTimestamp
saveConfig ()
