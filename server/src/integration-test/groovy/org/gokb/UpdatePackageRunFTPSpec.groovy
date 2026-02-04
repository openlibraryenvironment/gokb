package org.gokb

import com.k_int.ConcurrencyManagerService
import grails.core.GrailsApplication
import grails.gorm.transactions.Rollback
import grails.testing.mixin.integration.Integration
import org.apache.commons.io.IOUtils
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.gokb.cred.CuratoryGroup
import org.gokb.cred.Org
import org.gokb.cred.Package
import org.gokb.cred.Platform
import org.gokb.cred.RefdataCategory
import org.gokb.cred.Source
import org.gokb.cred.WebHookEndpoint
import org.mockftpserver.fake.*
import org.mockftpserver.fake.filesystem.*
import org.springframework.core.io.ClassPathResource
import spock.lang.Specification
import java.nio.charset.Charset

@Integration
@Rollback
class UpdatePackageRunFTPSpec extends Specification{

  GrailsApplication grailsApplication
  ConcurrencyManagerService concurrencyManagerService
  PackageSourceUpdateService packageSourceUpdateService


  private static final String HOME_DIR = "/";
  private static final String NON_KBART_FILE = "/dir/sample.txt";
  private static final String CONTENTS = "abcdef 1234567890";
  private static final String USER = "user";
  private static final String PASSWORD = "password";
  private static final int MOCKSERVER_PORT = 12345
  private FakeFtpServer ftpServer
  private CuratoryGroup new_cg




  def setup() {
    ftpServer = new FakeFtpServer()
    ftpServer.setServerControlPort(MOCKSERVER_PORT)
    FileSystem fileSystem = new UnixFakeFileSystem()
    fileSystem.add(new FileEntry(NON_KBART_FILE, CONTENTS))
    ftpServer.setFileSystem(fileSystem)
    ftpServer.addUserAccount(new UserAccount(USER, PASSWORD, HOME_DIR))

    ftpServer.start()

    new_cg = CuratoryGroup.findByName('TestGroup1') ?: new CuratoryGroup(name: "TestGroup1").save(flush: true)

    Org provider = Org.findByName("American Chemical Society") ?: new Org(name: "American Chemical Society").save(flush: true)
    Platform.findByName("Test Platform") ?: new Platform(name: "Test Platform", primaryUrl: "https://search.ebscohost.com", provider: provider).save(flush: true)
    WebHookEndpoint.findByName("whe1") ?: new WebHookEndpoint(name: "whe1" ,url: "ftp://localhost/dir", ba_username: USER, ba_password: PASSWORD).save(flush: true)
    Source.findByName("source1") ?: new Source(name: "source1", webEndpoint: WebHookEndpoint.findByName("whe1"), ftpUrl: "/kbart.txt", transferMethod: RefdataCategory.lookup('Source.TransferMethod', 'FTP')).save(flush: true)
    Package.findByName("package1") ?: new Package(name: "package1").save(flush: true)
    Package.findByName("package1").setSource(Source.findByName("source1"))
    Package.findByName("package1").save(flush: true)

    WebHookEndpoint.findByName("whe2") ?: new WebHookEndpoint(name: "whe2" ,url: "ftp://localhost/dir", ba_username: USER, ba_password: PASSWORD).save(flush: true)
    Source.findByName("source2") ?: new Source(name: "source2", webEndpoint: WebHookEndpoint.findByName("whe2"), ftpUrl: "/kbart_not_exists.txt", transferMethod: RefdataCategory.lookup('Source.TransferMethod', 'FTP')).save(flush: true)
    Package.findByName("package2") ?: new Package(name: "package2").save(flush: true)
    Package.findByName("package2").setSource(Source.findByName("source2"))
    Package.findByName("package2").save(flush: true)

    WebHookEndpoint.findByName("whe3") ?: new WebHookEndpoint(name: "whe3" ,url: "ftp://localhost/dir", ba_username: USER, ba_password: PASSWORD).save(flush: true)
    Source.findByName("source3") ?: new Source(name: "source3", webEndpoint: WebHookEndpoint.findByName("whe3"), ftpUrl: "/kbart_{YYYY-MM-DD}.txt", transferMethod: RefdataCategory.lookup('Source.TransferMethod', 'FTP')).save(flush: true)
    Package.findByName("package3") ?: new Package(name: "package3").save(flush: true)
    Package.findByName("package3").setSource(Source.findByName("source3"))
    Package.findByName("package3").save(flush: true)

    WebHookEndpoint.findByName("whe4") ?: new WebHookEndpoint(name: "whe4" ,url: "ftp://localhost/dir", ba_username: USER, ba_password: PASSWORD).save(flush: true)
    Source.findByName("source4") ?: new Source(name: "source4", webEndpoint: WebHookEndpoint.findByName("whe4"), ftpUrl: "/kbart_2026-01-11.txt", transferMethod: RefdataCategory.lookup('Source.TransferMethod', 'FTP')).save(flush: true)
    Package.findByName("package4") ?: new Package(name: "package4").save(flush: true)
    Package.findByName("package4").setSource(Source.findByName("source4"))
    Package.findByName("package4").save(flush: true)

  }

  def cleanup() {
    ftpServer.stop()
    CuratoryGroup.findByName('TestGroup1')?.expunge()
    Org.findByName("American Chemical Society")?.expunge()
    Platform.findByName("Test Platform")?.expunge()

    Package.findByName("package1")?.expunge()
    Source.findByName("source1")?.expunge()
    WebHookEndpoint.findByName("whe1")?.delete()

    Package.findByName("package2")?.expunge()
    Source.findByName("source2")?.expunge()
    WebHookEndpoint.findByName("whe2")?.delete()

    Package.findByName("package3")?.expunge()
    Source.findByName("source3")?.expunge()
    WebHookEndpoint.findByName("whe3")?.delete()

    Package.findByName("package4")?.expunge()
    Source.findByName("source4")?.expunge()
    WebHookEndpoint.findByName("whe4")?.delete()

  }

  void "test Mock FTP Server runs"() {

    FTPClient ftpClient = new FTPClient()
    ftpClient.connect("localhost", ftpServer.getServerControlPort())
    ftpClient.login(USER, PASSWORD);

    expect: "Directory exists"
    ftpClient.listDirectories().length > 0
    ftpServer.getServerControlPort() == MOCKSERVER_PORT

  }

   void "Test updateFromSource :: usual initial direct Import by filename"() {

    given: "requested Kbart exists without date mask"
    def kbart_file = new ClassPathResource("/test_ftp_kbart_update.txt")
    try(FileInputStream fis = new FileInputStream(kbart_file.getFile())){
      String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart.txt", fileContent))
    }

    Package p = Package.findByName("package1")
    def result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)

    expect: "file is found, package titles imported"
    result.result == 'OK'
    def titles = p.getTitles(true, 100, 0)
    titles.size() == 2

  }

  void "Test updateFromSource :: KBART does not exist on Server"() {
    given: "configured KBART file does not exist on server"
    Package p = Package.findByName("package2")
    def result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)
    expect:
    result.result == 'SKIPPED'
    result.messageCode == 'kbart.transmission.skipped.noFile'
    p.getTitles(true, 10, 0).size() == 0

  }



   void "Test updateFromSource :: KBART with date mask is found and imported"() {
    given: "FTP URL with date mask configured, file with date exists"

    def kbart_file = new ClassPathResource("/test_ftp_kbart_update.txt")
    try(FileInputStream fis = new FileInputStream(kbart_file.getFile())){
      String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-01-01.txt", fileContent))
    }
    Package p = Package.findByName("package3")
    def result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)
    expect:

    result.result == 'OK'
    def titles = p.getTitles(true, 100, 0)
    titles.size() == 2

  }


  void "Test updateFromSource :: KBART with latest date mask is found"() {
    given: "FTP URL with date mask configured, several files with dates exist"
    // source.ftpUrl = "kbart_{YYYY-MM-DD}.txt"
    def kbart_file = new ClassPathResource("/test_ftp_kbart_update.txt")
    try(FileInputStream fis = new FileInputStream(kbart_file.getFile())){
      String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
      String fakeContent = "AAAAAAAAAAAA"
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-02-01.txt", fileContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-01-01.txt", fakeContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2023-10-30.txt", fakeContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2025-12-24.txt", fakeContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/other_package_2026-02-02.txt", fakeContent))
    }
    //same WHE as in previous test
    Package p = Package.findByName("package3")
    def result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)
    expect: "find the file with the latest date, i.e. the only file with KBART content"
    result.result == 'OK'
    def titles = p.getTitles(true, 100, 0)
    titles.size() == 2
  }



  void "Test updateFromSource :: KBART with specified date is found"() {
    given: "FTP URL with specific date configured, several files with dates exist"
    // source.ftpUrl = "kbart_2026-01-11.txt"
    def kbart_file = new ClassPathResource("/test_ftp_kbart_update.txt")
    try(FileInputStream fis = new FileInputStream(kbart_file.getFile())){
      String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
      String fakeContent = "AAAAAAAAAAAA"
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-02-01.txt", fileContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-01-01.txt", "fakeContent"))
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2023-10-30.txt", fakeContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2025-12-24.txt", fakeContent))
      ftpServer.getFileSystem().add(new FileEntry("/dir/other_package_2026-02-02.txt", fakeContent))
    }
    Package p = Package.findByName("package4")
    def result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)
    expect: "find the file with the latest date, i.e. the only file with KBART content"
    result.result == 'OK'
    def titles = p.getTitles(true, 100, 0)
    titles.size() == 2
  }


   void "Test updateFromSource :: Package is updated"() {
     given: "Package is imported initially"
     //source.ftpUrl = "/kbart_{YYYY-MM-DD}.txt"
     def kbart_file = new ClassPathResource("/test_ftp_kbart_update.txt")
     try(FileInputStream fis = new FileInputStream(kbart_file.getFile())){
       String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
       ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-02-01.txt", fileContent))
     }
     // source with date mask
     Package p = Package.findByName("package3")
     def result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)

   expect: "Package should be imported correctly"

     result.result == 'OK'
     def titles = p.getTitles(true, 100, 0)
     titles.size() == 2

     def kbart_update_file = new ClassPathResource("/test_ftp_kbart_update_new_file.txt")
     try(FileInputStream fis = new FileInputStream(kbart_update_file.getFile())){
       String fileContent = IOUtils.toString(fis, Charset.defaultCharset())
       ftpServer.getFileSystem().add(new FileEntry("/dir/kbart_2026-02-03.txt", fileContent))
     }

    def update_result = packageSourceUpdateService.updateFromSource(p.id, null, null, new_cg.id, false, true)
     and: "Package should be updated by a new file"
      update_result.result == 'OK'
      p.getTitles(true, 100, 0).size() == 3

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



}
