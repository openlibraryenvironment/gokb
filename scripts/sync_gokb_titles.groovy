#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase

// Custom source host.


config.deferred = config.deferred ?: [:]

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '/gokb/oai/titles') { resp, body ->
  
    println("Cursor: ${body?.'ListRecords'?.'resumptionToken'?.'@cursor'} RT: ${body?.'ListRecords'?.'resumptionToken'?.text()} ")

    body?.'ListRecords'?.'record'.metadata.gokb.title.eachWithIndex { data, index ->
      
      println("Record ${index + 1}")
  
      // The basic record.
      def resourceFieldMap = addCoreItems ( data, ['type': 'Serial'])
      
      // Identifier count should be calculated after the irrelevant ones have been stripped.
      int identifier_count = resourceFieldMap?.identifiers?.size() ?: 0
      
      
      directAddFields (data, ['defaultAccessURL', 'publishedFrom', 'publishedTo', 
        'continuingSeries', 'OAStatus', 'imprint', 'issuer'], resourceFieldMap)

      String medium = "${cleanText(data.medium?.text())}"
      resourceFieldMap['medium'] = (medium != "" ? medium : 'Journal')

      // Might be several publishers each with it's own from and to...
      resourceFieldMap['publisher_history'] = data.publishers?.collect { pub ->
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
      
      if (identifier_count > 0) {
        // Process identified components now.
        resources.add(resourceFieldMap)
        
      } else {
        if ("${resourceFieldMap.name}" != "" && resourceFieldMap.name) {
          println("\tDefer processing of ${resourceFieldMap.name} due to lack of identifiers.")
          config.deferred[resourceFieldMap.name] = resourceFieldMap
        } else {
          println "\tIgnoring unnamed title."
        } 
      }
    }
  }
  
  resources.each {
    sendToTarget (path: '/gokb/integration/crossReferenceTitle', body: it)
  }
  
  // Save the config.
  println("Current config: ${config}")
  saveConfig()
  Thread.sleep(1000)
}

// Now that we have finished pulling down the titles we have a list of deferred "identifier-less" titles.
// We can send them now.
config.deferred.each { k, v ->
  sendToTarget (path: '/gokb/integration/crossReferenceTitle', body: v)
  Thread.sleep(25)
}

// Remove this here so we start from the beginning every time.
// config.remove('resumptionToken')

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
