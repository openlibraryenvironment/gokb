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

// Custom source host.

setSourceBase('http://localhost:8090/')
setSourceResponseType (JSON)

// Add some auth info to the request...
def sourceUser = config.sourceUser
def sourcePass = config.sourcePass
if (!config.sourceUser && !config.sourcePass) {
  println("Please supply authentication details for the source ${sourceBase}")
  config.putAll ([
    sourceUser: System.console().readLine ('Enter your username: ').toString(),
    sourcePass: System.console().readPassword ('Enter your password: ').toString()
  ])

  // Save to the file.
  saveConfig()
}

// Add the auth details.
getSource().auth.basic config.sourceUser, config.sourcePass

while ( moredata ) {
  
  def resources = []
  fetchFromSource (path: '/gokb/group/exportGroups') { resp, body ->

    body?.eachWithIndex { name, data, index ->
      
      println("Record ${index + 1}")
  
      // The record.
      def resourceFieldMap = [:]
      resourceFieldMap.putAll ( data )
      
      // Add the name.
      resourceFieldMap['name'] = name
      resources << resourceFieldMap
    } 
  }
  
  resources.each {
    sendToTarget (path: '/gokb/integration/assertGroup', body: it)
  }
  
  // Save the config.
  saveConfig()
}
