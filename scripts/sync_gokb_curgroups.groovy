#!groovy
@Grapes([
  @GrabResolver(name='mvnRepository', root='http://central.maven.org/maven2/'),
  @Grab(group='net.sourceforge.nekohtml', module='nekohtml', version='1.9.14'),
  @Grab(group='net.sourceforge.htmlunit', module='htmlunit', version='2.21'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.2'),
  @GrabExclude('org.codehaus.groovy:groovy-all')
])

@groovy.transform.BaseScript(GOKbSyncBase)
import GOKbSyncBase
import static groovyx.net.http.ContentType.JSON

setSourceResponseType (JSON)

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: "${sourceContext}/api/groups") { resp, body ->

    body?.result?.eachWithIndex { rec, index ->
      
      println("Record ${index + 1}")
      resources << rec
    } 
  }
  
  resources.each {
    sendToTarget (path: "${targetContext}/integration/assertGroup", body: it)
  }
  
  // Save the config.
  setLastRun ()
  saveConfig()
}
