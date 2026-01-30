package org.gokb

import com.k_int.ConcurrencyManagerService
import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.KBComponent
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import org.gokb.cred.User
import org.gokb.cred.WebHookEndpoint
import org.mockftpserver.fake.*
import org.mockftpserver.fake.filesystem.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.io.ClassPathResource
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.WebApplicationContext
import spock.lang.Specification

import java.nio.charset.Charset

@Integration
// @Transactional
@Rollback
class UpdatePackageRunFTPSpec extends Specification{

  GrailsApplication grailsApplication
  ConcurrencyManagerService concurrencyManagerService
  PackageSourceUpdateService packageSourceUpdateService

  @Autowired
  WebApplicationContext ctx

  private static final String HOME_DIR = "/";
  private static final String FILE = "/dir/sample.txt";
  private static final String CONTENTS = "abcdef 1234567890";
  private static final String USER = "user";
  private static final String PASSWORD = "password";
  private FakeFtpServer ftpServer
  private Package packageUnderInspection
  private CuratoryGroup new_cg
  private Org provider
  private WebHookEndpoint whe
  private Source source
  private Platform platform


  private String getUrlPath() {
    return "http://localhost:${serverPort}${grailsApplication.config.getProperty('server.servlet.context-path') ?: ''}".toString()
  }

  private void setupMockFtpBase(){
    ftpServer = new FakeFtpServer()
    ftpServer.setServerControlPort(21)
    FileSystem fileSystem = new UnixFakeFileSystem()
    fileSystem.add(new FileEntry(FILE, CONTENTS))
    ftpServer.setFileSystem(fileSystem)
    ftpServer.addUserAccount(new UserAccount(USER, PASSWORD, HOME_DIR))

    ftpServer.start()
  }

  void setUpKbartExistsNormalName(){
    def kbart_file = new ClassPathResource("/test_ftp_kbart_update.txt")
    try(FileInputStream fis = new FileInputStream(kbart_file.getFile())){
      String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart.txt", fileContent))
    }
  }

  def setup() {
    this.setupMockFtpBase()

    new_cg = CuratoryGroup.findByName('TestGroup1') ?: new CuratoryGroup(name: "TestGroup1").save(flush: true)

    provider = Org.findByName("American Chemical Society") ?: new Org(name: "American Chemical Society").save(flush: true)
    platform = Platform.findByName("Test Platform") ?: new Platform(name: "Test Platform", primaryUrl: "https://search.ebscohost.com", provider: provider).save(flush: true)
    whe = WebHookEndpoint.findByName("MOCK FTP on Localhost") ?: new WebHookEndpoint(name: "MOCK FTP on Localhost" ,url: "ftp://localhost", ba_username: USER, ba_password: PASSWORD).save(flush: true)
    source = Source.findByName("Test Source") ?: new Source(name: "Test Source", webEndpoint: whe, ftpUrl: "/dir/kbart.txt", transferMethod: RefdataCategory.lookup('Source.TransferMethod', 'FTP')).save(flush: true)

    // TODO: Pflichtfelder
    packageUnderInspection = Package.findByName("Update Package") ?: new Package(name: "Update Package").save(flush: true)
    packageUnderInspection.setSource(source)

    packageUnderInspection.save(flush: true)


  }

  def cleanup() {
    ftpServer.stop()
    CuratoryGroup.findByName('TestGroup1')?.expunge()
    Org.findByName("American Chemical Society")?.expunge()
    Platform.findByName("Test Platform")?.expunge()

    //Source.findByName("Test Source")?.expunge()
    //whe.delete()
    Package.findByName("Update Package")?.expunge()
    //TODO: von KBComponent ableiten???
    //WebHookEndpoint.findByName("MOCK FTP on Localhost")?.expunge()

  }


  void "Test updateFromSource :: usual initial Import by filename"() {

    given: "asked Kbart exists without date mask"
    setUpKbartExistsNormalName()

    def user = User.findByUsername('admin')

    /* PackageSourceUpdateService service = new PackageSourceUpdateService()

    ConcurrencyManagerService.Job pkg_job = concurrencyManagerService.createJob { pjob ->
      service.updateFromSource(packageUnderInspection.id, null, pkg_job, new_cg.id, false, true)
    }
    Package.withNewSession {
      pkg_job.startOrQueue()
    }
    def job_result = pkg_job.get()
    */

    /* ConcurrencyManagerService.Job pkg_job = concurrencyManagerService.createJob { pjob ->
      packageSourceUpdateService.updateFromSource(packageUnderInspection.id, null, pjob, new_cg.id, false, true)
    }

    pkg_job.startOrQueue()
    def job_result = pkg_job.get()
    */

    def result = packageSourceUpdateService.updateFromSource(packageUnderInspection.id, null, null, new_cg.id, false, true)

    System.out.println("222222222222222222: " + result)
    expect: "file is found, package titles imported"
    def titles = packageUnderInspection.getTitles(true, 100, 0)
    titles != null
    System.out.println("######### " + titles)


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

    /* and: "find file and read its contents"
    ftpClient.changeWorkingDirectory("dir")
    def files = ftpClient.listFiles()
    System.out.println("+++ " + files)

    String fileContent = ""
    try(InputStream is = ftpClient.retrieveFileStream("kbart.txt")){
      fileContent = IOUtils.toString(is, Charset.defaultCharset())
    }
    */
    //System.out.println(fileContent)



  }






}
