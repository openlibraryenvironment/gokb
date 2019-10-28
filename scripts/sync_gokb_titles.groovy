#!groovy

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase

// Custom source host.


config.deferred = config.deferred ?: [:]

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/oai/titles") { resp, body ->
  
    println("Cursor: ${body?.'ListRecords'?.'resumptionToken'?.'@cursor'} RT: ${body?.'ListRecords'?.'resumptionToken'?.text()} ")

    body?.'ListRecords'?.'record'.eachWithIndex { rec, index ->

      def data = rec.metadata.gokb.title
      
      println("Record ${index + 1}")
  
      // The basic record.
      def resourceFieldMap = addCoreItems ( data )

      def type = cleanText(data.type?.text())

      if (!type || type == 'JournalInstance' || type == 'Serial') {
        resourceFieldMap.type = 'Serial'
      }
      else if (type == 'DatabaseInstance') {
        resourceFieldMap.type = 'Database'
      }
      else if (type == 'BookInstance'|| type == 'Monograph') {
        resourceFieldMap.type = 'Book'
      }

      
      // Identifier count should be calculated after the irrelevant ones have been stripped.
      int identifier_count = resourceFieldMap?.identifiers?.size() ?: 0
      
      
      directAddFields (data, ['defaultAccessURL', 'publishedFrom', 'publishedTo', 
        'continuingSeries', 'OAStatus', 'imprint', 'issuer',"editionNumber","editionDifferentiator", "editionStatement", "volumeNumber", "summaryOfContent", "firstAuthor", "firstEditor", "dateFirstInPrint", "dateFirstOnline"], resourceFieldMap)

      String medium = "${cleanText(data.medium?.text())}"
      resourceFieldMap['medium'] = medium

      // Might be several publishers each with it's own from and to...
      resourceFieldMap['publisher_history'] = data.publishers?.publisher?.collect { pub ->
        [
          'name': cleanText(pub.name.text()),
          'status': cleanText(pub.status.text()),
          'startDate': cleanText(pub.startDate.text()),
          'endDate': cleanText(pub.endDate.text())
        ]
      } ?: []

      println("Publishers: ${data.publishers} -> ${resourceFieldMap.publisher_history}")
      
      resourceFieldMap['historyEvents'] = data.history?.historyEvent?.collect { he ->
        println("\tHandle history event")
        [
          'date': cleanText(he.date.text()),
          'from': he?.from?.collect { fr ->
            convertHistoryEvent(fr)
          } ?: [],
          'to': he?.to?.collect { to ->
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
          def df_name = resourceFieldMap.name

          if (!config.deferred) {
            config.deferred = ["${df_name}": resourceFieldMap ]
          }
          config.deferred[resourceFieldMap.name] = resourceFieldMap
        } else {
          println "\tIgnoring unnamed title."
        }
      }

      config.lastTimestamp = rec.header.datestamp.text()
    }
  }
  
  resources.each {
    sendToTarget (path: "${targetContext}/integration/crossReferenceTitle", body: it)
  }
  
  // Save the config.
  println("Current config: ${config}")
  saveConfig()
  Thread.sleep(100)
}

setLastRun ()
saveConfig ()

// Now that we have finished pulling down the titles we have a list of deferred "identifier-less" titles.
// We can send them now.
config.deferred.each { k, v ->
  sendToTarget (path: "${targetContext}/integration/crossReferenceTitle", body: v)
  Thread.sleep(25)
}

println("Total: ${total}, Errors: ${errors}")

// Remove this here so we start from the beginning every time.
// config.remove('resumptionToken')

private convertHistoryEvent(evt) {
  // convert the evt structure to a json object and add to lst
  def result = [
    'title' : cleanText(evt.title.text()),
    'uuid' : cleanText(evt.uuid.text()),
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
