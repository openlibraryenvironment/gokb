#!groovy

@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='javax.mail', module='mail', version='1.4.7'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.5.2'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.5.2'),
  @Grab(group='commons-net', module='commons-net', version='3.5'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])

import static groovy.json.JsonOutput.*
import groovy.json.JsonSlurper

import com.gargoylesoftware.htmlunit.*

import java.time.Instant

import org.apache.http.entity.mime.content.StringBody

import groovyx.net.http.*
import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST

import org.apache.commons.io.FilenameUtils

abstract class GOKbSyncBase extends Script {
  
  String sourceBase = 'http://gokb.openlibraryfoundation.org/'
  int source_timeout_retry = 3
  long source_timeout_wait = 5 * 1000 // (5 seconds)
  def sourceResponseType = XML
  
  String targetBase = 'http://localhost:8080/'
  String sourceContext = '/gokb'
  String targetContext = '/gokb'
  def targetResponseType = JSON
  
  // More data defaults to true.
  def moredata = true
  def total = 0
  def errors = 0
  def params = [:]
  
  /** None-config vals below **/
  private HTTPBuilder source
  private HTTPBuilder target
  def config = null
  boolean dryRun = false
  def cfg_file
  
  protected HTTPBuilder getTarget () {
    if (!target) {
      // Create the target.
      target = new HTTPBuilder(targetBase, targetResponseType)
      target.auth.basic config.uploadUser, config.uploadPass

      target.getClient().getParams().setParameter("http.connection.timeout", 30000)
      target.getClient().getParams().setParameter("http.socket.timeout", 1500000)
    }
    
    target
  }
  
  protected HTTPBuilder getSource () {
    if (!source) {
      // Create the source.
      source = new HTTPBuilder(sourceBase, sourceResponseType)

      if(sourceResponseType == XML) {
        source.headers = [Accept: 'application/xml']
      }
      else {
        source.headers = [Accept: 'application/json']
      }
    }
    
    source
  }
  
  private String fileName
  private def loadConfig () {
    String fileName = "${this.class.getSimpleName().replaceAll(/\_/, "-")}-cfg.json"
    cfg_file = new File("./${fileName}")
    
    if ( cfg_file.exists() ) {
      config = new JsonSlurper().parseText(cfg_file.text)

      if (config.sourceBase) {
        sourceBase = config.sourceBase
      }

      if (config.sourceContext != null) {
        sourceContext = config.sourceContext
      }

      println("Using source ${sourceBase} with context path '${sourceContext}'")

      if (config.targetBase) {
        targetBase = config.targetBase
      }

      if (config.targetContext != null) {
        targetContext = config.targetContext
      }

      println("Using target ${targetBase} with context path '${targetContext}'")

      if (!config.uploadUser || !config.uploadPass) {
        println("The provided config file does not include user credentials!")
        println("Please enter your credentials for ${targetBase}")

        config.uploadUser = System.console().readLine ('Enter your username: ').toString()
        config.uploadPass = System.console().readPassword ('Enter your password: ').toString()
      }
    }
    else {
      println("No config found please supply authentication details for the target ${targetBase}")
      config = [
        uploadUser: System.console().readLine ('Enter your username: ').toString(),
        uploadPass: System.console().readPassword ('Enter your password: ').toString()
      ]
      
      // Save to the file.
      saveConfig()
      
      println("Saved config file to ${fileName}")
    }
    
    println("Using config ${config}")
    config
  }
  
  protected saveConfig () {
    cfg_file.delete()
    config.remove('deferred')
    
    cfg_file << prettyPrint(toJson(config))
  }
  
  protected cleanText(String text) {
    text?.trim()?.replaceAll(/\s{2,}/, ' ')
  }
  
  protected def sendToTarget(Map parameters = [:], def successClosure = null) {
    
    def returnData
    try {
      if ( dryRun ) {
        println "${prettyPrint(toJson(parameters['body']))}"
      } else {
    
        getTarget().request(POST, JSON) { req ->
        
          if (parameters['path']) {
            uri.path = parameters['path']
          }

          uri.query = [fullsync: 'true']
          
          if (parameters['body']) {
            body = parameters['body']
          }

          println("${uri}")
      
          response.success = { resp, data ->
            println "${data.result ?: 'SUCCESS'} - ${resp.status} ${data.message}"
            if (successClosure) {
              successClosure (resp, data)
            }

            if (data.result && data.result == "ERROR") {
              errors++

              if (data.errors) {
                data.errors.each { e ->
                  println "       - ${e}"
                }
              }
            }

            total++
            
            returnData = [
              result : data,
              status : 'success'
            ]
          }
      
          response.failure = { err ->
            returnData = [
              result : "Failed on http request to source (see stack trace)",
              status : 'error'
            ]
            println("ERROR: ${err.getStatus()} - ${err.getContentType()} -- ${err.getData()}")
          }
        }
      }
      
    } catch ( Exception e ) {
      returnData = [
        result : "Fatal error sending data",
        status : 'error'
      ]
      e.printStackTrace()
      
      println("Error when sending data ${parameters}")
      System.exit(0)
    }
    
    returnData
  }
  
  protected Map fetchFromSource(Map parameters = [:], def successClosure = null) {

    def returnData

    println("${this.args}")
    
    if (!parameters.containsKey('query')) {
      parameters['query'] = [verb: 'ListRecords', metadataPrefix: 'gokb']
    }
    if (config.resumptionToken) parameters['query']['resumptionToken'] = config.resumptionToken

    if (config.lastRun && (!config.resumptionToken || config.resumptionToken.size() == 0) && this.args?.size() > 0 && this.args.contains('--update') ) {
      parameters['query']['from'] = config.lastRun
    }
    
    boolean success = false // flag to terminate loop.
    while (!success) {
      try {
        getSource().request(GET, getSourceResponseType()) { req ->
          
          if (parameters['path']) {
            uri.path = parameters['path']
          }

          uri.query = parameters['query']

          println("${uri}")
          
          response.success = { resp, body ->
            
            // Set the flag to terminate the loop.
            success = true
            
            // Execute the supplied closure to operate on the response data.
            def result = successClosure( resp, body )
            returnData = [
              'result' : result,
              'status' : 'success'
            ]

            body.error?.each { er ->
              println("${er}")
            }
            
            // Set the resumption token last...
            config.resumptionToken = body?.ListRecords?.resumptionToken?.text()
            
            // Also use the token to flag more data...
            if (dryRun) {
              moredata = false
            } else {
              moredata = config.resumptionToken
            }
          }
          
          // Fail with error.
          response.error = { err ->
            returnData = [
              result : "Failed on http request to source (see stack trace)",
              status : 'error'
            ]
            println(err)
          }
        }
      } catch (HttpResponseException ex) {
        def resp = ex.getResponse()
        println "Got response code ${resp.status}"
        
        if (resp.status == 504 && source_timeout_retry > 0) {
          source_timeout_retry --
          // Retry...
          println ("Retrying (${source_timeout_retry} remaining attempts) after ${source_timeout_wait} delay")
          Thread.sleep(source_timeout_wait)
        } else {
          // Throw the exception...
          throw ex
        }
      }
    }
    
    // Return the data.
    returnData
  }
  
  private handleFile = { fa ->
        
    // Add core items of each source.
    def fileMap = addCoreItems (fa)
    directAddFields (fa, ['guid','md5', 'uploadName', 'uploadMimeType', 'filesize', 'doctype', 'content'], fileMap)
    
//    new File("./${FilenameUtils.getName(fileMap['uploadName'])}").withOutputStream {
//      it.write bytes
//    }

    fileMap
  }
  
  private handleSource = { source ->
    
    // Add core items of each source.
    def fileMap = addCoreItems (source)
    
    directAddFields (source, [
      'url','defaultAccessURL', 'explanationAtSource',
      'contextualNotes', 'frequency', 'ruleset',
      'defaultSupplyMethod', 'defaultDataFormat', 'responsibleParty'], fileMap)
  }
  
  protected directAddFields (def data, Collection<String> fields = [], Map addTo = [:]) {
    if (data) {
      fields?.each { f ->
        data[f]?.each { d ->
          def val = cleanText ( d.text() )
          if ( val && !addTo[f] ) { 
            addTo[f] = val;
          } 
          else if ( !val ) { 
            // println("skipping empty field ${f}")
          }
          else if ( addTo[f] ) {
            println("skipping duplicate field ${f}")
          }
        }
      }
    }
    
    addTo
  }
  
  protected Map addCoreItems (def data, Map addTo = [:]) {
    
    if (data) {
      
      directAddFields (data, ['name', 'status', 'editStatus', 'shortcode'], addTo)
    
      if (data.identifiers && data.identifiers.size() > 0) {
        
        def ids = []
        data.identifiers?.identifier?.each {
          
          // Only include namespaces that are not 'originEditUrl'
          if ( cleanText(it.'@namespace'.text()).toLowerCase() != 'originediturl' ) {
            def final_val = cleanText(it.'@value'?.text())

            if (it.'@namespace'.text() == 'zdb' && final_val?.trim() && !final_val.contains('-')) {
              final_val = final_val.substring(0,final_val.length()-2) + "-" + final_val[-1..-1]
            }

            ids.add( [ type:it.'@namespace'.text(), value: final_val ] )
          }
        }
        
        if (ids) {
          // Identifiers
          addTo['identifiers'] = ids
        }
      }

      if (data.'@uuid' && data.'@uuid'.size() > 0) {
        addTo['uuid'] = data.'@uuid'.text()
      }
      
      // Additional properties.
      if (data.additionalProperties?.additionalProperty && data.additionalProperties?.additionalProperty.size() > 0) {
        addTo['additionalProperties'] = data.additionalProperties.additionalProperty?.collect ({
          [ name:it.'@name'.text(), value: cleanText(it.'@value'?.text()) ]
        }) ?: []
      }
      
      // Variant names.
      if (data.variantNames && data.variantNames.size() > 0) {
        addTo['variantNames'] = data.variantNames.variantName?.collect ({
          cleanText( it.text() )
        }) ?: []
      }
      
      // File attachments.
      if (data.fileAttachments && data.fileAttachments.size() > 0) {
        addTo['fileAttachments'] = data.fileAttachments.fileAttachment?.collect ( handleFile )
      }
      
      // Source.
      if (data.source && data.source.size() > 0) {
        addTo['source'] = handleSource ( data.source )
      }
      
      // Curatory groups are not against KBComponent but are used accross a few types.
      if (data.curatoryGroups?.group?.name?.size() ?: 0 > 0) {
        addTo['curatoryGroups'] = data.curatoryGroups.group.name.collect {
          it.text()
        }
      }
      
      // Roles.
      if (data.roles?.role?.size() ?: 0 > 0) {
        addTo['roles'] = data.roles.role.collect {
          it.text()
        }
      }
      
    }
    
    addTo
  }

  protected setLastRun() {

    if (config.lastTimestamp) {
      Instant lastTimestamp = Instant.parse(config.lastTimestamp)

      if (config.lastRun && Instant.parse(config.lastRun) == lastTimestamp) {
        config.lastRun = lastTimestamp.plusSeconds(1).toString()
      }
      else if (config.lastRun && Instant.parse(config.lastRun) == lastTimestamp.plusSeconds(1)) {
        println 'No changes ..'
      }
      else {
        config.lastRun = config.lastTimestamp
      }
    }
  }
  
  private def cleanup() {
    println 'Cleanup resources...'
    target?.shutdown()
    target = null
    source?.shutdown()
    source = null
    
    // We should save the config here if clean exit...
    if (!moredata) {
      config.remove('resumptionToken')
      saveConfig()
    }
  }
  
  def run() {
    try {
      
      // Load config.
      loadConfig ()

      // Run actual script code.
      runCode()
      
    } finally {
      cleanup()
      println "Done!"
    }
  }
  
  // Abstract method as placeholder for the script body to be inserted into.
  abstract def runCode()
}
