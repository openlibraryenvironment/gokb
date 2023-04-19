package org.gokb

import grails.gorm.transactions.Transactional
import grails.io.IOUtils
import liquibase.util.StringUtils
import org.grails.web.json.JSONObject

import java.time.LocalDateTime

@Transactional
class TSVEgestionService {

  def grailsApplication

  /**
   * Generically create a TSV file from any JSONArray structure.
   * @param json The Json data to be exported.
   * @param fields The list of fields to be exported, optionally mapped with the name of the TSV column.
   * @param filterEmptyColumns Columns defined in fields that do not provide data in any of the json objects can be left
   *        out of the TSV result. This might be at the expense of performance, since it requires an iterative check
   *        before writing the output.
   */
  File jsonToTsv (List data, Map<String, String> fields, boolean filterEmptyColumns) {
    File resultFile = new File(grailsApplication.config.gokb.tsvExportTempDirectory + File.separator +
            LocalDateTime.now().toString() + ".tsv")
    FileWriter fileWriter = new FileWriter(resultFile, true)
    Map<String, String> refinedFields = refineFields(fields, filterEmptyColumns, data)
    StringBuilder stringBuilder = new StringBuilder()
    refinedFields.entrySet().forEach { it ->
      if (!StringUtils.isEmpty(it.value)) {
        stringBuilder.append(it.value + "\t")
      }
      else {
        stringBuilder.append(it.key + "\t")
      }
    }
    endLine(stringBuilder)
    data.forEach { dataEntry ->
      refinedFields.entrySet().each { def field ->
        if (dataEntry.get(field.key) != null) {
          stringBuilder.append(dataEntry.get(field.key).toString().replaceAll("[\r\n]+", ", "))
        }
        stringBuilder.append("\t")
      }
      endLine(stringBuilder)
      if (stringBuilder.size() > 1024) {
        fileWriter.write(stringBuilder.toString())
        stringBuilder = new StringBuilder()
      }
    }
    fileWriter.write(stringBuilder.toString())
    fileWriter.close()
    resultFile
  }


  def sendTsvAsDownload (def response, File tsvFile) {
    InputStream inFile = new FileInputStream(tsvFile)
    String fileName = tsvFile.getName()

    response.setContentType('text/tab-separated-values')
    response.setHeader("Content-Disposition", "attachment; filename=\"${fileName}\"")
    response.setHeader("Content-Encoding", "UTF-8")
    response.setContentLength(tsvFile.bytes.length)

    def out = response.outputStream
    IOUtils.copy(inFile, out)
    inFile.close()
    out.close()
  }


  private StringBuilder endLine (StringBuilder stringBuilder) {
    int len = stringBuilder.size()
    if (stringBuilder.getAt(len-1) == "\t") {
      stringBuilder.replace(len-1, len, "\n")
    }
    else {
      stringBuilder.append("\n")
    }
    stringBuilder
  }


  private def refineFields(Map<String, String> fields, boolean filterEmptyColumns, List data) {
    Map<String, String> refinedFields = [:]
    if (fields == null || fields.isEmpty()) {
      if (filterEmptyColumns) {
        data.forEach { dataEntry ->
          dataEntry.each { it ->
            if (it.value) {
              refinedFields.put(it.key, null)
            }
          }
        }
      }
      else {
        data.forEach { dataEntry ->
          dataEntry.each { it ->
            refinedFields.put(it.key, null)
          }
        }
      }
    }
    else {
      if (filterEmptyColumns) {
        fields.each { it ->
          if (!StringUtils.isEmpty(it.value)) {
            refinedFields.put(it.key, it.value)
          }
        }
      }
      else {
        refinedFields.putAll(fields)
      }
    }
    refinedFields.remove("_embedded")
    refinedFields.remove("_links")
    refinedFields
  }
}
