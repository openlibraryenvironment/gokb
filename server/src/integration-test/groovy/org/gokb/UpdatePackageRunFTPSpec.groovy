package org.gokb

import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import org.apache.commons.net.ftp.FTPClient
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import org.gokb.cred.WebHookEndpoint
import org.mockftpserver.fake.*
import org.mockftpserver.fake.filesystem.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

@Integration
@Rollback
class UpdatePackageRunFTPSpec extends Specification{

  GrailsApplication grailsApplication

  @Autowired
  WebApplicationContext ctx

  BlockingHttpClient http

  private static final String HOME_DIR = "/";
  private static final String FILE = "/dir/sample.txt";
  private static final String CONTENTS = "abcdef 1234567890";
  private static final String USER = "user";
  private static final String PASSWORD = "password";
  private FakeFtpServer ftpServer

  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path') ?: ''}".toString()
  }

  private void setupMockFtpBase(){
    ftpServer = new FakeFtpServer();
    ftpServer.setServerControlPort(0);  // use any free port
    FileSystem fileSystem = new UnixFakeFileSystem();
    fileSystem.add(new FileEntry(FILE, CONTENTS));
    ftpServer.setFileSystem(fileSystem);
    ftpServer.addUserAccount(new UserAccount(USER, PASSWORD, HOME_DIR))
    ftpServer.start();
  }

  void setUpBasic(){

  }

  def setup() {
    this.setupMockFtpBase()

    if (!http) {
      http = HttpClient.create(new URL(getUrlPath())).toBlocking()
    }

    def new_cg = CuratoryGroup.findByName('TestGroup1') ?: new CuratoryGroup(name: "TestGroup1").save(flush: true)
    def acs_org = Org.findByName("American Chemical Society") ?: new Org(name: "American Chemical Society").save(flush: true)

    WebHookEndpoint whe = new WebHookEndpoint(name: "MOCK FTP on Localhost" ,url: "ftp://localhost", ba_username: USER, ba_password: PASSWORD).save(flush: true)
    Source mock_src = new Source(webEndpoint: whe, ftpUrl: FILE, transferMethod: RefdataCategory.lookup('Source.TransferMethod', 'FTP')).save(flush: true)
    // TODO: Pflichtfelder
    Package pckg = new Package(name: "Update Package", source: mock_src).save(flush: true)

  }

  def cleanup() {
    ftpServer.stop()
  }

  void "test WebEndpointService extractUrlParts :: split parts and format them correct"() {

    when: "The Slashes are not set correctly in URL Field"
    String endpointUrl = "ftp://servername.com/home/"
    String sourceUrl = "/filename.txt"

    def res = new WebEndpointService().extractFtpUrlParts(endpointUrl, sourceUrl)


    then: "The URL is normalized and splitted correctly into parts"

    res.hostname == "servername.com"
    res.directory == "/home/"
    res.filename == "filename.txt"
    res.complete == "ftp://servername.com/home/filename.txt"

  }



  void "test Mock FTP Server runs"() {

    FTPClient ftpClient = new FTPClient()
    ftpClient.connect("localhost", ftpServer.getServerControlPort())
    ftpClient.login(USER, PASSWORD);

    expect: "Directory exists"
    ftpClient.listDirectories().length > 0
  }


 /* void "Test updateFromSource :: "() {

    given:

  } */




}
