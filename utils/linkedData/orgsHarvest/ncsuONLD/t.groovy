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
import static groovy.json.JsonOutput.*
import static groovyx.net.http.ContentType.URLENC
import groovy.util.slurpersupport.GPathResult
import org.apache.http.*
import org.apache.http.protocol.*



if ( args.length < 2 ) {
  println("Usage:  groovy ./JuspToBibJson.groovy \"<<base url of service>>\"");
  println("   eg:  groovy \"file:./ONLD.jsonld\" \"http://localhost:8080/gokb/integration/assertJsonldOrg\"");
  System.exit(0);
}

println("Client uri is ${args[1]}");

def api = new RESTClient(args[1])
def rest_upload_pass = ""
System.in.withReader { it ->
  print 'admin pass:'
  rest_upload_pass = it.readLine()
}


// Add preemtive auth
api.client.addRequestInterceptor( new HttpRequestInterceptor() {
  void process(HttpRequest httpRequest, HttpContext httpContext) {
    String auth = "admin:${rest_upload_pass}"
    String enc_auth = auth.bytes.encodeBase64().toString()
      httpRequest.addHeader('Authorization', 'Basic ' + enc_auth);
    }
})




// def http = new RESTClient(args[0]);
// URL apiUrl = new URL('http://www.lib.ncsu.edu/ld/onld/downloads/ONLD.jsonld')


URL apiUrl = new URL('file:./ONLD.jsonld')
def data = new JsonSlurper().parse(apiUrl)

int count = 0

data.'@graph'.each { org ->
  println "[${count++}] ${org.'@id'} posting..."
//   post(api, org)
}

// def post(h, obj) {
//   println("Post...${h}");
//   h.post( // path : 'api/uploadBibJson',
//           requestContentType : ContentType.JSON,
//           body : obj) { resp, json ->
//     println("Result: ${resp}, ${json}");
//   }

// }

