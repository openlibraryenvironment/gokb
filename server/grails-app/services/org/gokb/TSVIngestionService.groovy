package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import java.security.MessageDigest

import org.gokb.cred.*
import org.mozilla.universalchardet.UniversalDetector

class TSVIngestionService {

  def grailsApplication
  def sessionFactory

  def updatePackage(def pkgId,
                    def dfId,
                    IdentifierNamespace title_id_ns,
                    boolean async,
                    boolean incremental,
                    def userId,
                    def groupId,
                    boolean dry_run,
                    boolean skip_invalid,
                    boolean cleanup,
                    Job job = null) {

    Package.withNewSession {
      Package pkg = Package.get(pkgId)
      DataFile datafile = DataFile.get(dfId)
      User request_user = User.get(userId)
      IdentifierNamespace idns = title_id_ns ? IdentifierNamespace.get(title_id_ns) : null
      CuratoryGroup active_group = CuratoryGroup.get(groupId)

      IngestKbartRun myRun = new IngestKbartRun(pkg,
                                                datafile,
                                                idns,
                                                async,
                                                incremental,
                                                request_user,
                                                active_group,
                                                dry_run,
                                                skip_invalid,
                                                cleanup)
      return myRun.start(job)
    }
  }

  def analyseFile(temp_file) {
    def result = [:]
    result.filesize = 0

    log.debug("analyze...")

    // Create a checksum for the file..
    MessageDigest md5_digest = MessageDigest.getInstance("MD5")
    InputStream is

    if (temp_file instanceof File) {
      is = new FileInputStream(temp_file)
    }
    else {
      is = temp_file
    }

    BufferedInputStream md5_is = new BufferedInputStream(is)
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

  def handleTempFile(deposit_token, def inputfile = null) {
    def baseUploadDir = grailsApplication.config.getProperty('baseUploadDir') ?: '/tmp/gokb/ingest'
    log.debug("handleTempFile...")
    def sub1 = deposit_token.substring(0,2)
    def sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    def temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    def temp_file = new File(temp_file_name)

    if (inputfile) {
      log.debug("Copying uploaded file ..")
      // Copy the upload file to a temporary space
      inputfile.transferTo(temp_file);
    }

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
