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


def platforms = [
    'sciencemag.org',
    'pubs.acs.org',
    'wiley.com',
    'link.aip.org',
    'rsc.org',
    'arjournals.annualreviews.org',
    'www.annualreviews.org',
    'www.bioone.org',
    'jhu.edu',
    'sagepub.com',
    'www.nature.com',
    'www.embojournal.org',
    'www.emboreports.org',
    'www.labanimal.com',
    'www.laboratoryinvestigation.org',
    'journals.cambridge.org',
    'ProjectEuclid.org',
    'microscopy-today.com',
    'www.cambridge.org',
    'content.karger.com',
    'als.dukejournals.org',
    'americanliterature.dukejournals.org',
    'americanspeech.dukejournals.org',
    'boundary2.dukejournals.org',
    'cameraobscura.dukejournals.org',
    'commonknowledge.dukejournals.org',
    'complit.dukejournals.org',
    'cssaame.dukejournals.org',
    'culturalpolitics.dukejournals.org',
    'differences.dukejournals.org',
    'easts.dukejournals.org',
    'ecl.dukejournals.org',
    'ethnohistory.dukejournals.org',
    'fhs.dukejournals.org',
    'genre.dukejournals.org',
    'glq.dukejournals.org',
    'hahr.dukejournals.org',
    'hope.dukejournals.org',
    'jhppl.dukejournals.org',
    'jmems.dukejournals.org',
    'jmt.dukejournals.org',
    'labor.dukejournals.org',
    'mq.dukejournals.org',
    'minnesotareview.dukejournals.org',
    'mlq.dukejournals.org',
    'ngc.dukejournals.org',
    'nka.dukejournals.org',
    'novel.dukejournals.org',
    'pedagogy.dukejournals.org',
    'philreview.dukejournals.org',
    'poeticstoday.dukejournals.org',
    'positions.dukejournals.org',
    'publicculture.dukejournals.org',
    'rhr.dukejournals.org',
    'smallaxe.dukejournals.org',
    'ssh.dukejournals.org',
    'socialtext.dukejournals.org',
    'saq.dukejournals.org',
    'theater.dukejournals.org',
    'tikkun.dukejournals.org',
    'www.emeraldinsight.com',
    'sciencedirect.com',
    'ieeexplore.ieee.org',
    'booksandjournals.brillonline.com',
    'gsabulletin.gsapubs.org',
    'geology.gsapubs.org',
    'geosphere.gsapubs.org',
    'lithosphere.gsapubs.org',
    'www.geosociety.org',
    'eeg.geoscienceworld.org',
    'specialpapers.gsapubs.org',
    'memoirs.gsapubs.org',
    'iopscience.iop.org',
    'www.ingentaconnect.com',
    'jorthod.maneyjournals.org',
    'maneypublishing.com',
    'De Gruyter',
    'dl.acm.org',
    'portal.acm.org',
    'liebertpub.com',
    'asm.org',
    'aip.org',
    'physiology.org'
]



if ( args.length < 1 ) {
  println("   eg:  groovy ./syncPlatforms.groovy \"http://localhost:8080/gokb/integration/assertJsonldPlatform\"");
  System.exit(0);
}

println("Client uri is ${args[0]}");

def api = new RESTClient(args[0])
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



platforms.each { p ->
  println(p)
  def platform = ['skos:prefLabel':p]
  post(api,platform);
}

public void post(h, obj) {
  println("Post...${h}");
  h.post( // path : 'api/uploadBibJson',
          requestContentType : ContentType.JSON,
          body : obj) { resp, json ->
    println("Result: ${resp}, ${json}");
  }
}

