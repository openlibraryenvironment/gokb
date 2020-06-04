package gokbg3

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import spock.lang.Specification
import spock.lang.Shared
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.beans.factory.annotation.*
import org.springframework.web.context.WebApplicationContext
import grails.core.GrailsApplication
import org.gokb.cred.*


@groovy.util.logging.Slf4j
@Integration
@Rollback
class OaiSpec extends Specification {

    GrailsApplication grailsApplication

    @Shared
    RestBuilder rest = new RestBuilder()

    @Autowired
    WebApplicationContext ctx

    def setup() {
      def test_pkg = Package.findByName('Test Package 1') ?: new Package(name:'Test Package 1').save(flush:true)
      def test_plt = Platform.findByName('Test Platform') ?: new Platform(name:'Test Platform').save(flush:true)

      def title1 = JournalInstance.findByName('Test Title 1') ?: new JournalInstance(name:'Test Title 1').save(flush:true)

      def eissn1 = Identifier.findByValue('1234-3456') ?: new Identifier(value: '1234-3456', namespace: IdentifierNamespace.findByValue('eissn')).save(flush:true)
      def issn1 = Identifier.findByValue('1234-4567') ?: new Identifier(value: '1234-4567', namespace: IdentifierNamespace.findByValue('issn')).save(flush:true) 

      def tipp1 = new TitleInstancePackagePlatform().save(flush:true)

      new Combo(fromComponent: test_pkg, toComponent: tipp1, type: RefdataCategory.lookup('Combo.Type','Package.Tipps'), status: RefdataCategory.lookup('Combo.Status','Active')).save(flush:true)
      new Combo(fromComponent: test_plt, toComponent: tipp1, type: RefdataCategory.lookup('Combo.Type','Platform.HostedTipps'), status: RefdataCategory.lookup('Combo.Status','Active')).save(flush:true)
      new Combo(fromComponent: title1, toComponent: tipp1, type: RefdataCategory.lookup('Combo.Type','TitleInstance.Tipps'), status: RefdataCategory.lookup('Combo.Status','Active')).save(flush:true)

      def coverageStatement = [startDate: new Date(), startVolume: "1", startIssue: "1"]

      tipp1.addToCoverageStatements(coverageStatement)
    }

    def cleanup() {
    }

    // This is a test REST call 
    void "test ListRecords response status"() {
      when:
        // RestResponse resp = authRest.get("http://localhost:${serverPort}/search/search")
        RestResponse resp = rest.get("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/oai/packages?verb=ListRecords&metadataPrefix=gokb")

      then:
        // println(resp.json)
        resp.status == 200
    }

    void "test ListRecords package response"() {
      when: 
        RestResponse resp = rest.get("http://localhost:${serverPort}${grailsApplication.config.server.contextPath ?: ''}/oai/packages?verb=ListRecords&metadataPrefix=gokb")

      then:

        log.info("${resp.xml.'OAI-PMH'?.'ListRecords'?.'record'?.'metadata'?.'gokb'?.'package'?.'name'?.text()}")
        resp.xml.'OAI-PMH'?.'ListRecords'?.'record'?.'metadata'?.'gokb'?.'package'?.'name'?.text() != null
    }
}
