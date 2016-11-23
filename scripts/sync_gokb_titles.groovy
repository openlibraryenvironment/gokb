#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase

// Custom source host.

setSourceBase('http://localhost:8090/')
long added = 0
long skipped = 0

// This script is different from the rest. During the first pass we will only act on titles with identifiers present.
for (config.pass = config.pass ?: 1; config.pass<=2; config.pass++) {
  
  // Save to persist pass number.
  saveConfig()
  
  while ( moredata ) {
    
    def resources = []
    fetchFromSource (path: '/gokb/oai/titles') { resp, body ->
  
      body?.'ListRecords'?.'record'.metadata.gokb.title.eachWithIndex { data, index ->
        
        println("Record ${index + 1}")
    
        // The basic record.
        def resourceFieldMap = addCoreItems ( data, ['type': 'Serial'])
        
        // Identifier count should be calculated after the irrelevant ones have been stripped.
        int identifier_count = resourceFieldMap?.identifiers?.size() ?: 0
        
        if ((config.pass > 1 && (identifier_count == 0) ) || (config.pass == 1 && (identifier_count > 0))) {
          directAddFields (data, ['defaultAccessURL', 'publishedFrom', 'publishedTo', 
            'continuingSeries', 'OAStatus', 'imprint', 'issuer'], resourceFieldMap)
    
          String medium = "${cleanText(data.medium?.text())}"
          resourceFieldMap['medium'] = (medium != "" ? medium : 'Journal')
    
          // Might be several publishers each with it's own from and to...
          resourceFieldMap['publisher_history'] = data.publisher?.collect { pub ->
            directAddFields (pub, ['name','startDate','endDate','status'])
          } ?: []
          
          resourceFieldMap['historyEvents'] = data.history?.historyEvent?.collect { he ->
            println("\tHandle history event")
            [
              'date': cleanText(he.date.text()),
              'from': he?.from?.collect { fr ->
                println("\tConvert from title in history event : ${fr}")
                convertHistoryEvent(fr)
              } ?: [],
              'to': he?.to?.collect { to ->
                println("\tConvert to title in history event : ${to}")
                convertHistoryEvent(to)
              } ?: []
            ]
          } ?: []
          
          resources.add(resourceFieldMap)
          added ++
        } else {
          if (config.pass == 1) {
            println("\tSkipping title without identifiers.")
          } else {
            println("\tSkipping identified title on second pass.")
          }
          skipped ++
        }
      }
    }
    int sleep = 2000
    resources.each {
      sendToTarget (path: '/gokb/integration/crossReferenceTitle', body: it)
      sleep = 50
    }
    Thread.sleep(sleep)
  }
  
  // Remove this here so we start from the beginning every time.
  config.remove('resumptionToken')
  moreData = true
}

// Also clear pass number.
config.remove('pass')
println "Added: ${added}, Skipped: ${skipped}"

private convertHistoryEvent(evt) {
  // convert the evt structure to a json object and add to lst
  def result = [
    'title' : cleanText(evt.title[0].text()),
    'identifiers' : evt.identifiers.identifier.collect { id ->
      [
        type: cleanText(id.'@namespace'.text()),
        value: cleanText(id.'@value'.text())
      ]
    }
  ]
  println("\tResult of convertHistoryEvent : ${result}")
  result
}
