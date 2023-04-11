package org.gokb

import grails.gorm.transactions.Transactional
import liquibase.util.StringUtils
import org.grails.web.json.JSONArray
import org.grails.web.json.JSONObject

import java.time.LocalDateTime

@Transactional
class TSVEgestionService {

  def grailsApplication

  /**
   * Generically create a TSV output from any json structure.
   * @param json The Json data to be exported.
   * @param fields The list of fields to be exported, optionally mapped with the name of the TSV column.
   * @param filterEmptyColumns Columns defined in fields that do not provide data in any of the json objects can be left
   *        out of the TSV result. This might be at the expense of performance, since it requires an iterative check
   *        before writing the output.
   */
  File jsonToTsv (JSONArray jsonArray, Map<String, String> fields, boolean filterEmptyColumns) {
    File result = new File(grailsApplication.config.gokb.tsvExportTempDirectory + File.separator +
            LocalDateTime.now().toString() + ".tsv")
    FileWriter fileWriter = new FileWriter(result, true)
    Map<String, String> refinedFields = refineFields(fields, filterEmptyColumns, jsonArray)
    StringBuilder stringBuilder = new StringBuilder()
    refinedFields.forEach { it ->
      if (!StringUtils.isEmpty(it.value)) {
        stringBuilder.append(it.value + "\t")
      }
      else {
        stringBuilder.append(it.key + "\t")
      }
    }
    endLine(stringBuilder)
    jsonArray.forEach { JSONObject jsonRow ->
      refinedFields.each { def field ->
        stringBuilder.append(jsonRow.get(field.key)).append("\t")
      }
      endLine(stringBuilder)
      if (stringBuilder.size() > 1024) {
        fileWriter.write(stringBuilder.toString())
        stringBuilder = new StringBuilder()
      }
    }
    fileWriter.write(stringBuilder.toString())
    fileWriter.close()
    result
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


  private def refineFields (Map<String, String> fields, boolean filterEmptyColumns, JSONArray jsonArray) {
    Map<String, String> refinedFields = [:]
    if (fields == null || fields.isEmpty()) {
      if (filterEmptyColumns) {
        jsonArray.forEach { JSONObject jsonLine ->
          jsonLine.each { it ->
            if (!StringUtils.isEmpty(it.value)) {
              refinedFields.put(it.key, null)
            }
          }
        }
      }
      else {
        jsonArray.forEach { JSONObject jsonLine ->
          jsonLine.each { it ->
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
    refinedFields
  }
}
