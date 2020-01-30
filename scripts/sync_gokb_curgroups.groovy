#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '${sourceContext}/api/groups') { resp, body ->

    body?.result?.eachWithIndex { rec, index ->
      
      println("Record ${index + 1}")
      resources << rec
    } 
  }
  
  resources.each {
    sendToTarget (path: '${targetContext}/integration/assertGroup', body: it)
  }
  
  // Save the config.
  setLastRun ()
  saveConfig()
}
