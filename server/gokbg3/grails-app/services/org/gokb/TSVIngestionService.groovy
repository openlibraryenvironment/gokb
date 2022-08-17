package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import java.security.MessageDigest

import org.gokb.cred.*
import org.mozilla.universalchardet.UniversalDetector

@Transactional
class TSVIngestionService {

  def grailsApplication
  def sessionFactory

  def updatePackage(Package pkg,
                    DataFile datafile,
                    IdentifierNamespace title_id_ns,
                    boolean async,
                    boolean incremental,
                    def request_user,
                    def active_group,
                    boolean dry_run,
                    Job job = null) {
    def session = sessionFactory.currentSession

    if (session) {
      IngestKbartRun myRun = new IngestKbartRun(pkg,
                                                datafile,
                                                title_id_ns,
                                                async,
                                                incremental,
                                                request_user,
                                                active_group,
                                                dry_run)
      return myRun.start(job)
    }
    else {
      Package.withNewSession {
        IngestKbartRun myRun = new IngestKbartRun(pkg,
                                                  datafile,
                                                  title_id_ns,
                                                  async,
                                                  incremental,
                                                  request_user,
                                                  active_group,
                                                  dry_run)
        return myRun.start(job)
      }
    }
  }

  def analyseFile(temp_file) {
    def result = [:]
    result.filesize = 0

    log.debug("analyze...")

    // Create a checksum for the file..
    MessageDigest md5_digest = MessageDigest.getInstance("MD5")
    FileInputStream fis = new FileInputStream(temp_file)
    BufferedInputStream md5_is = new BufferedInputStream(fis)
    UniversalDetector detector = new UniversalDetector()
    byte[] md5_buffer = new byte[8192]
    int md5_read = 0

    while( (md5_read = md5_is.read(md5_buffer, 0, 8192)) >= 0) {
      md5_digest.update(md5_buffer, 0, md5_read)
      detector.handleData(md5_buffer, 0, md5_read)
      result.filesize += md5_read
    }

    detector.dataEnd()
    md5_is.close()
    byte[] md5sum = md5_digest.digest()
    result.md5sumHex = new BigInteger(1, md5sum).toString(16)
    result.encoding = detector.getDetectedCharset()

    log.debug("MD5 is ${result.md5sumHex}, encoding is ${result.encoding}")
    result
  }

  def copyUploadedFile(inputfile, deposit_token) {
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '/tmp/gokb/ingest'
    log.debug("copyUploadedFile...")
    def sub1 = deposit_token.substring(0,2)
    def sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    def temp_file = new File(temp_file_name)

    // Copy the upload file to a temporary space
    inputfile.transferTo(temp_file);

    temp_file
  }

  def createTempFile(deposit_token) {
    def baseUploadDir = grailsApplication.config.baseUploadDir ?: '/tmp/gokb/ingest'
    def sub1 = deposit_token.substring(0,2)
    def sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    def temp_file = new File(temp_file_name)

    temp_file
  }

  private def validateUploadDir(path) {
    File f = new File(path)
    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }
}
