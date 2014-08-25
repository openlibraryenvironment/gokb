// Grapes are a way to import dependencies into a groovy scriptlet.
// Here we really only need MySQL, a number of secondary useful modules are commented out
// They are handy for related tasks

@GrabConfig(systemClassLoader=true)

@Grapes([
  // Following libs useful for calling out to REST web services
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2'),
  @Grab(group='org.apache.httpcomponents', module='httpclient', version='4.0'),
  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.5.0'),
  @Grab(group='org.apache.httpcomponents', module='httpmime', version='4.1.2'),
  @GrabExclude('xml-apis:xml-apis')
])


// Handy library for handling JDBC statements
import groovyx.net.http.*
import org.apache.http.entity.mime.*
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST
import static groovyx.net.http.ContentType.TEXT
import static groovyx.net.http.ContentType.HTML
import static groovyx.net.http.ContentType.JSON
import org.apache.http.entity.mime.content.*
import java.nio.charset.Charset
import groovy.json.JsonSlurper


println("Usage:  groovy ./JuspToBibJson.groovy \"<<base url of service>>\"");
println("   eg:  groovy ./JuspToBibJson.groovy \"http://localhost:8080/demo/\"");

// println("Client uri is ${args[0]}");

// def http = new RESTClient(args[0]);
// URL apiUrl = new URL('http://www.lib.ncsu.edu/ld/onld/downloads/ONLD.jsonld')
URL apiUrl = new URL('file:./ONLD.jsonld')
def data = new JsonSlurper().parse(apiUrl)

data.'@graph'.each {
  println it.'@id'
}

print("Got data");


def post(h, obj) {
  println("Post...${h}");
  h.post( path : 'api/uploadBibJson',
          requestContentType : ContentType.JSON,
          body : obj) { resp, json ->
    println("Result: ${resp}, ${json}");
  }

}

