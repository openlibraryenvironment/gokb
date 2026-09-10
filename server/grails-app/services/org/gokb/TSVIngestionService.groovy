package org.gokb

import com.k_int.ConcurrencyManagerService.Job

import grails.gorm.transactions.Transactional

import java.security.MessageDigest

import org.gokb.cred.*
import org.mozilla.universalchardet.UniversalDetector

class TSVIngestionService {

  def grailsApplication
  def sessionFactory

  public Map updatePackage(Long pkgId,
                    Long dfId,
                    Long title_id_ns,
                    boolean async,
                    boolean incremental,
                    Long userId,
                    Long groupId,
                    boolean dry_run,
                    boolean skip_invalid,
                    boolean cleanup,
                    Job job = null,
                    Long title_id_ns_serial,
                    Long title_id_ns_monograph) {

    Package.withNewSession { session ->
      Package pkg = Package.get(pkgId)
      DataFile datafile = DataFile.get(dfId)
      User request_user = User.get(userId)
      IdentifierNamespace idns = title_id_ns ? IdentifierNamespace.get(title_id_ns) : null
      IdentifierNamespace idns_serial = title_id_ns_serial ? IdentifierNamespace.get(title_id_ns_serial) : null
      IdentifierNamespace idns_monograph = title_id_ns_monograph ? IdentifierNamespace.get(title_id_ns_monograph) : null
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
                                                cleanup,
                                                idns_serial,
                                                idns_monograph)
      return myRun.start(job, session)
    }
  }

  public Map analyseFile(temp_file) {
    Map result = [:]
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

  public File handleTempFile(deposit_token, def inputfile = null) {
    log.debug("handleTempFile...")
    String baseUploadDir = grailsApplication.config.getProperty('baseUploadDir') ?: '/tmp/gokb/ingest'
    String sub1 = deposit_token.substring(0,2)
    String sub2 = deposit_token.substring(2,4)
    validateUploadDir("${baseUploadDir}/${sub1}/${sub2}")
    String temp_file_name = "${baseUploadDir}/${sub1}/${sub2}/${deposit_token}"
    File temp_file = new File(temp_file_name)

    if (inputfile) {
      log.debug("Copying uploaded file ..")
      // Copy the upload file to a temporary space
      inputfile.transferTo(temp_file);
    }

    temp_file
  }

  private void validateUploadDir(path) {
    File f = new File(path)

    if ( ! f.exists() ) {
      log.debug("Creating upload directory path")
      f.mkdirs();
    }
  }
}
