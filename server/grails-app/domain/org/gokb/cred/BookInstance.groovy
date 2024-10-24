package org.gokb.cred

import com.k_int.ClassUtils
import org.gokb.IntegrationController
import org.grails.web.json.JSONObject

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

import java.time.LocalDateTime

import static grails.async.Promises.*


@Slf4j
class BookInstance extends TitleInstance {

  @Transient
  def titleLookupService

  String editionDifferentiator
  String editionStatement
  String volumeNumber
  String firstAuthor
  String firstEditor
  Date dateFirstInPrint
  Date dateFirstOnline
  String summaryOfContent

  private static refdataDefaults = [
      "TitleInstance.medium": "Book"
  ]

  static mapping = {
    includes TitleInstance.mapping
    editionDifferentiator column: 'bk_editionDifferentiator'
    editionStatement column: 'bk_editionStatement'
    volumeNumber column: 'bk_volume'
    dateFirstInPrint column: 'bk_dateFirstInPrint'
    dateFirstOnline column: 'bk_dateFirstOnline'
    summaryOfContent column: 'bk_summaryOfContent'
    firstAuthor column: 'bk_firstAuthor'
    firstEditor column: 'bk_firstEditor'
  }

  static constraints = {
    editionDifferentiator(nullable: true, blank: false)
    editionStatement(nullable: true, blank: false)
//    volumeNumber(validator: { val, obj ->
//      if (obj.hasChanged('volumeNumber') && val) {
//        if (!(val ==~ "^\\d+\$")) {
//          return ["typeMismatch.java.lang.Integer"]
//        }
//      }
//    })
    dateFirstInPrint(nullable: true, blank: false)
    dateFirstOnline(nullable: true, blank: false)
    summaryOfContent(nullable: true, blank: false)
    firstAuthor(nullable: true, blank: false)
    firstEditor(nullable: true, blank: false)
  }

  @Override
  String getLogEntityId() {
    "${this.class.name}:${id}"
  }

  @Override
  public String getNiceName() {
    return "Book";
  }

  public static final String restPath = "/titles"

  /**
   * Auditable plugin, on change
   *
   * See if properties that might impact the mapping of this instance to a work have changed.
   * If so, fire the appropriate event to cause a remap.
   */

  def afterUpdate() {

    // Currently, serial items are mapped based on the name of the journal. We may need to add a discriminator property
    if ((hasChanged('name')) ||
        (hasChanged('editionStatement')) ||
        (hasChanged('componentDiscriminator'))) {
      log.debug("Detected an update to properties for ${id} that might change the work mapping. Looking up");
//       submitRemapWorkTask();
    }
    touchAllDependants()
  }

  @Override
  protected def generateComponentHash() {

    this.componentDiscriminator = generateBookDiscriminator(['volumeNumber': volumeNumber, 'editionDifferentiator': editionDifferentiator, 'firstAuthor': firstAuthor])

    // To try and find instances
    this.componentHash = GOKbTextUtils.generateComponentHash([normname, componentDiscriminator]);

    // To find works
    this.bucketHash = GOKbTextUtils.generateComponentHash([normname]);
  }

  def afterInsert() {
//     submitRemapWorkTask();
  }

  public static String generateBookDiscriminator(Map relevantFields) {
    def result = null;

    def normVolume = generateNormname(relevantFields.volumeNumber)
    def normEdD = generateNormname(relevantFields.editionDifferentiator)
    def normFirstAuthor = generateNormname(relevantFields.firstAuthor)

    if (normVolume?.size() > 0 || normEdD?.size() > 0 || normFirstAuthor?.size() > 0) {
      result = "${normVolume ? 'v.' + normVolume : ''}${normEdD ? 'ed.' + normEdD : ''}${normFirstAuthor ? 'a:' + normFirstAuthor : ''}".toString()
    }
    result
  }

  def submitRemapWorkTask() {
    log.debug("BookInstance::submitRemapWorkTask");
    def tls = grailsApplication.mainContext.getBean("titleLookupService")
    def map_work_task = task {
      // Wait for the onSave to complete, and the system to release the session, thus freeing the data to
      // other transactions
      synchronized (this) {
        Thread.sleep(3000);
      }
      tls.remapTitleInstance('org.gokb.cred.BookInstance:' + this.id)
    }

    // We cannot wait for the task to complete as the transaction has to complete in order
    // for the Instance to become visible to other transactions. Therefore there has to be
    // a delay between completing the Instance update, and attempting to resolve the work.
    onComplete([map_work_task]) { mapResult ->
      // Might want to add a message to the system log here
    }
  }

  public static def validateDTO(JSONObject titleDTO, locale) {
    def result = TitleInstance.validateDTO(titleDTO, locale)
    def valErrors = [:]

    // shortening some db fields with standard size of 255 if needed.
    // does not invalidate the DTO!
    ['firstAuthor', 'firstEditor'].each { key ->
      if (titleDTO.containsKey(key)) {
        if (titleDTO[key].size() > 255) {
          valErrors.put(key, [message: "too long", baddata: titleDTO[key]])
          titleDTO[key] = titleDTO[key].substring(0, 251).concat(" ...")
          log.warn("value in key ’${key}’ was clipped to: ${titleDTO[key]}")
        }
      }
    }

    if (titleDTO.dateFirstInPrint) {
      LocalDateTime dfip = GOKbTextUtils.completeDateString(titleDTO.dateFirstInPrint, false)
      if (!dfip) {
        valErrors.put('dateFirstInPrint', [message: "Unable to parse", baddata: titleDTO.remove('dateFirstInPrint')])
      }
    }

    if (titleDTO.dateFirstOnline) {
      LocalDateTime dfo = GOKbTextUtils.completeDateString(titleDTO.dateFirstOnline, false)
      if (!dfo) {
        valErrors.put('dateFirstOnline', [message: "Unable to parse", baddata: titleDTO.remove('dateFirstOnline')])
      }
    }

    if (valErrors.size() > 0) {
      if (result.errors) {
        result.errors.putAll(valErrors)
      }
      else {
        result.errors = valErrors
      }
    }
    result
  }
}
