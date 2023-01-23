package org.gokb


import com.github.ladutsko.isbn.*
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder

import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.validator.routines.ISSNValidator
import org.apache.commons.validator.routines.UrlValidator

import org.gokb.cred.*
import org.gokb.GOKbTextUtils

class ValidationService {

  def TSVIngestionService

  static final Map KNOWN_COLUMNS = [
    publication_title: [
      mandatory: true,
      validator: [
        name: "checkTitleString",
        args: []
      ]
    ],
    print_identifier: [
      mandatory: false,
      discriminator: "publication_type",
      namespaces: [
        'Serial': 'issn',
        'Monograph': 'isbn'
      ],
      validator: [
        name: "checkKbartIdentifier",
        args: ["_colName", "publication_type"]
      ]
    ],
    online_identifier: [
      mandatory: false,
      discriminator: "publication_type",
      namespaces: [
        'Serial': 'eissn',
        'Monograph': 'pisbn'
      ],
      validator: [
        name: "checkKbartIdentifier",
        args: ["_colName", "publication_type"]
      ]
    ],
    date_first_issue_online: [
      mandatory: false,
      pubType: "Serial",
      validator: [
        name: "checkDate",
        args: []
      ]
    ],
    num_first_vol_online: [
      mandatory: false,
      pubType: "Serial"
    ],
    num_first_issue_online: [
      mandatory: false,
      pubType: "Serial"
    ],
    date_last_issue_online: [
      mandatory: false,
      pubType: "Serial",
      validator: [
        name: "checkDate",
        args: []
      ]
    ],
    num_last_vol_online: [
      mandatory: false,
      pubType: "Serial",
    ],
    num_last_issue_online: [
      mandatory: false,
      pubType: "Serial"
    ],
    title_url: [
      mandatory: true,
      validator: [
        name: "checkUrl",
        args: []
      ]
    ],
    first_author: [
      mandatory: false,
    ],
    title_id: [
      mandatory: true
    ],
    embargo_info: [
      mandatory: false,
      validator: [
        name: "checkEmbargoCode",
        args: []
      ]
    ],
    coverage_depth: [
      mandatory: false,
      validator: [
        name: "checkCoverageDepth",
        args: []
      ]
    ],
    notes: [
      mandatory: false
    ],
    publisher_name: [
      mandatory: false
    ],
    publication_type: [
      mandatory: true
    ],
    date_monograph_published_print: [
      mandatory: false,
      pubType: "Monograph",
      validator: [
        name: "checkDate",
        args: []
      ]
    ],
    date_monograph_published_online: [
      mandatory: false,
      pubType: "Monograph",
      validator: [
        name: "checkDate",
        args: []
      ]
    ],
    monograph_volume: [
      mandatory: false,
      pubType: "Monograph"
    ],
    monograph_edition: [
      mandatory: false,
      pubType: "Monograph"
    ],
    first_editor: [
      mandatory: false,
      pubType: "Monograph"
    ],
    parent_publication_title_id: [
      mandatory: false
    ],
    preceding_publication_title_id: [
      mandatory: false
    ],
    access_type: [
      mandatory: false,
      validator: [
        name: "checkAccessType",
        args: []
      ]
    ],
    zdb_id: [
      mandatory: false,
      namespaces: [
        'Serial': 'zdb',
      ],
      validator: [
        name: "checkKbartIdentifier",
        args: ["_colName", "publication_type"]
      ]
    ]
  ]

  static int NUM_MANDATORY_COLS = 5

  static ISSNValidator ISSN_VAL = new ISSNValidator()

  def generateKbartReport(InputStream kbart, IdentifierNamespace titleIdNamespace = null, boolean strict = false) {
    def result = [
      valid: true,
      message: "",
      rows: [total: 0, error: 0, warning: 0],
      errors: [
        missingColumns: [],
        rows: [:],
        type: [:]
      ],
      warnings: [
        missingColumns: [],
        rows: [:],
        type: [:]
      ]
    ]

    CSVReader csv = initReader(kbart)

    Map col_positions = [:]
    String[] header = csv.readNext()

    header = header.collect { it.toLowerCase().trim() }

    int ctr = 0

    header.each {
      col_positions[it] = ctr++
    }

    KNOWN_COLUMNS.each { colName, info ->
      if (info.mandatory && !header.contains(colName)) {
        result.errors.missingColumns.add(colName)
        result.valid = false
      }
      else if (!header.contains(colName) && colName != 'zdb_id') {
        result.warnings.missingColumns.add(colName)
      }
    }

    String[] nl = csv.readNext()
    int rowCount = 0

    if (result.errors.missingColumns.size() == 0) {
      while (nl != null) {
        rowCount++

        if (nl.size() > 1 && nl.size() != header.size()) {
          result.rows.total++
          result.rows.error++
          result.errors.rows["${rowCount}"] = [
            columnsCount: [
              message: "Inconsistent column count (${nl.size()} <> ${header.size()})!",
              messageCode: "kbart.errors.tabsCountRow",
              args: [nl.size(), header.size()]
            ]
          ]
          addOrIncreaseTypedCount(result, 'columnCount', 'errors')

          result.valid = false
        }
        else if (nl.size() >= NUM_MANDATORY_COLS) {
          result.rows.total++

          def row_result = checkRow(nl, rowCount, col_positions, titleIdNamespace, strict)

          if (row_result.errors) {
            result.rows.error++
            result.valid = false
            result.errors.rows["${rowCount}"] = row_result.errors

            row_result.errors.each { error_key, error_list ->
              addOrIncreaseTypedCount(result, error_key, 'errors')
            }
          }
          if (row_result.warnings) {
            result.rows.warning++
            result.warnings.rows["${rowCount}"] = row_result.warnings

            row_result.warnings.each { warn_key, warn_list ->
              addOrIncreaseTypedCount(result, warn_key, 'warnings')
            }
          }
        }
        else {
          log.debug("Found and skipped short row ${nl}")
          result.rows.warning++

          if (!result.warnings.rows["${rowCount}"]) {
            result.warnings.rows["${rowCount}"] = [:]
          }

          result.warnings.rows["${rowCount}"]["shortRow"] = [
            message: "Skipped short/empty row!",
            messageCode: "kbart.errors.shortRow"
          ]
        }

        nl = csv.readNext()
      }
      result.message = "File processing finished after ${result.rows.total} (${result.rows.error} errors)."
    }
    else {
      log.debug("Missing mandatory columns... skipping file processing!")
      result.message = "File processing was skipped due to missing mandatory columns!"
    }

    csv.close()

    result
  }

  private void addOrIncreaseTypedCount(result, String column, String type) {
    if (!result[type].type[column]) {
      result[type].type[column] = 1
    }
    else {
      result[type].type[column]++
    }
  }

  private CSVReader initReader (the_data) {
    final CSVParser parser = new CSVParserBuilder()
    .withSeparator('\t' as char)
    .withIgnoreQuotations(true)
    .build()

    CSVReader csv = new CSVReaderBuilder(
        new BufferedReader(
          new InputStreamReader(
            new BOMInputStream(
              the_data,
              ByteOrderMark.UTF_16LE,
              ByteOrderMark.UTF_16BE,
              ByteOrderMark.UTF_32LE,
              ByteOrderMark.UTF_32BE,
              ByteOrderMark.UTF_8
            ),
            'UTF-8'
          )
        )
    ).withCSVParser(parser)
    .build()

    return csv
  }

  def checkRow(String[] nl, int rowCount, Map col_positions, IdentifierNamespace titleIdNamespace = null, boolean strict = false) {
    def result = [errors: [:], warnings: [:]]
    def valid_ids = []
    def pubTypeVal = nl[col_positions['publication_type']].trim()
    def pubType = checkPubType(pubTypeVal)

    for (key in col_positions.keySet()) {
      def trimmed_val = nl[col_positions[key]].trim()

      if (trimmed_val.length() > 4092) {
        result.errors["longVals"] = [
          message: "Unexpectedly long value in row -- Probably miscoded quote in line.",
          messageCode: "kbart.errors.longValsFile",
          args: []
        ]
      }

      if (trimmed_val.contains('�') && !result.errors["replacementChars"]) {
        result.errors["replacementChars"] = [
          message: "Value contains UTF-8 replacement characters!",
          messageCode: "kbart.errors.replacementCharsRow",
          args: []
        ]
      }

      if (KNOWN_COLUMNS[key]) {
        if (KNOWN_COLUMNS[key].mandatory && !trimmed_val) {
          result.errors[key] = [
            message: "Missing value in mandatory column '${key}'",
            messageCode: "kbart.errors.missingVal",
            args: [key]
          ]
        }
        else if (key == 'title_id' && titleIdNamespace) {
          def field_valid_result = checkIdForNamespace(trimmed_val, titleIdNamespace)

          if (!field_valid_result) {
            result.errors[key] = [
              message: "Identifier value '${trimmed_value}' in column 'title_id' is not valid!",
              messageCode: "kbart.errors.illegalVal",
              args: [trimmed_val]
            ]
          }
        }
        else if (!pubType && (key == 'online_identifier' || key == 'print_identifier')) {
          log.debug("Skipping ID columns due to missing publication_type")
        }
        else if (KNOWN_COLUMNS[key].validator && trimmed_val) {
          def final_args = [trimmed_val] + KNOWN_COLUMNS[key].validator.args?.collect { it == "_colName" ? key : nl[col_positions[it]] }
          def field_valid_result = "${KNOWN_COLUMNS[key].validator.name}"(*final_args)

          if (!field_valid_result || (strict && field_valid_result != trimmed_val && key != 'publication_title')) {
            result.errors[key] = [
              message: "Value '${trimmed_val}' is not valid!",
              messageCode: "kbart.errors.illegalVal",
              args: [trimmed_val]
            ]
          }
          else if (field_valid_result != trimmed_val && key != 'publication_title') {
            result.warnings[key] = [
              message: "Value '${trimmed_val}' will be automatically replaced by'${field_valid_result}'!",
              messageCode: "kbart.errors.correctedVal",
              args: [trimmed_val, field_valid_result]
            ]
          }
        }
      }
    }

    if (pubTypeVal && (!pubType || (strict && pubType != 'Serial' && pubType != 'Monograph'))) {
      result.errors["publication_type"] = [
        message: "Publication type '${pubTypeVal}' is not valid!",
        messageCode: "kbart.errors.illegalType",
        args: [pubTypeVal]
      ]
    }

    if (!col_positions['online_identifier'] && !col_positions['print_identifier'] && !col_positions['title_id']) {
      result.errors["noIds"] = [
        message: "This row contains no usable identifiers and will not be processed!",
        messageCode: "kbart.errors.noIds",
        args: []
      ]
    }

    if (col_positions['date_first_issue_online'] && col_positions['date_last_issue_online']) {
      def date_order = checkDatePair(nl[col_positions['date_first_issue_online']], nl[col_positions['date_last_issue_online']])

      if (date_order == 'error') {
        result.errors['date_last_issue_online'] = [
          message: "The end date must be after the start date.",
          messageCode: "validation.dateRange",
          args: []
        ]
      }
    }

    result
  }

  def checkEmbargoCode(String value) {
    return (value ==~ ~"^(([RP][1-9][0-9]*[DMY])|(R[1-9][0-9]*[DMY];P[1-9][0-9]*[DMY]))\$" ? value : false)
  }

  def checkPubType(String value) {
    def result = null
    RefdataValue resolvedType = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', value)

    if (resolvedType) {
      result = resolvedType.value
    }

    result
  }

  def checkAccessType(String value) {
    def final_val = null

    if (value in ['P', 'p']) {
      final_val = 'P'
    }
    else if (value in ['F', 'f']) {
      final_val = 'F'
    }
    final_val
  }

  def checkCoverageDepth(String value) {
    def result = null
    def final_val = value

    if (value?.toLowerCase() in ['full text', 'volltext']) {
      final_val = 'fulltext'
    }

    RefdataValue resolvedType = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', final_val)

    if (resolvedType) {
      result = value
    }

    result
  }

  def checkTitleString(String value) {
    return GOKbTextUtils.cleanTitleString(value) ?: null
  }

  def checkKbartIdentifier(String value, String column, String pubType) {
    def result = null
    def final_type = checkPubType(pubType)

    if (final_type && KNOWN_COLUMNS[column]?.namespaces?."${final_type}") {
      def namespace = IdentifierNamespace.findByValue(KNOWN_COLUMNS[column]?.namespaces?."${final_type}")
      result = checkIdForNamespace(value, namespace)
    }

    result
  }

  def checkIdForNamespace(String value, IdentifierNamespace titleIdNamespace) {
    def result = null

    if (titleIdNamespace.value in ['isbn', 'pisbn']) {
      try {
        def valid_isbn = ISBN.parseIsbn(value)

        result = value
      }
      catch(ISBNException ie) {}
    }
    else if (titleIdNamespace.value in ['issn', 'eissn']) {
      def valid_issn = ISSN_VAL.isValid(value.toUpperCase())

      if (valid_issn) {
        result = value
      }
    }
    else if (titleIdNamespace.value == 'zdb') {
      result = checkZdbId(value)
    }
    else if (titleIdNamespace.pattern) {
      if (value ==~ ~"${titleIdNamespace.pattern}") {
        result = value
      }
    }
    else {
      result = value
    }

    result
  }

  def checkZdbId(String zdbId) {
    def result = null

    if (zdbId ==~ ~"^\\d{7,10}-[\\dxX]\$") {
      def parts = zdbId.split('-')

      int number = Integer.valueOf(parts[0])
      int checkDigit = 0
      def factor = 2

      while (number > 0){
        checkDigit += (number % 10) * factor
        number = number / 10
        factor++
      }
      checkDigit %= 11

      if (checkDigit == 10 && parts[1] in ['x', 'X'] || checkDigit == Integer.valueOf(parts[1])) {
        result = zdbId
      }
    }

    result
  }

  def checkDate(String value) {
    def result = null

    def full_date = GOKbTextUtils.completeDateString(value)

    if (full_date) {
      result = value
    }

    result
  }

  def checkUrl(String value) {
    return new UrlValidator().isValid(value.trim()) ? value : null
  }

  def checkDatePair(String startDate, String endDate) {
    def final_start = GOKbTextUtils.completeDateString(startDate)
    def final_end = GOKbTextUtils.completeDateString(endDate)

    if (final_start && final_end && final_end < final_start) {
      return 'error'
    }
    else {
      return 'ok'
    }
  }

  def checkNewComponentName(String value, String componentType) {
    def result = [result: 'OK']
    def defined_types = [
        "Package",
        "Org",
        "JournalInstance",
        "Journal",
        "Serial",
        "BookInstance",
        "Book",
        "DatabaseInstance",
        "Database",
        "Platform",
        "TitleInstancePackagePlatform",
        "TIPP",
        "TitleInstance",
        "Title",
        "OtherInstance",
        "Other"
    ]
    def final_type = componentType.capitalize()

    if (final_type in defined_types) {
      if (final_type == 'TIPP') {
        final_type = 'TitleInstancePackagePlatform'
      }
      else if (final_type == 'Book' || final_type == 'Monograph') {
        final_type = 'BookInstance'
      }
      else if (final_type == 'Journal' || final_type == 'Serial') {
        final_type = 'JournalInstance'
      }
      else if (final_type == 'Database') {
        final_type = 'DatabaseInstance'
      }
      else if (final_type == 'Title') {
        final_type = 'TitleInstance'
      }
      else if (final_type == 'Other') {
        final_type = 'OtherInstance'
      }
    }
    else {
      result.result = 'ERROR'
      result.errors = [[message: 'Unknown component type!', messageCode: 'validation.unknownType', value: componentType]]

      return result
    }

    Class type_class = Class.forName('org.gokb.cred.' + final_type)
    String cleaned_val = GOKbTextUtils.cleanTitleString(value)

    if (cleaned_val) {
      result.cleanedVal = cleaned_val
      def test_obj = null

      try {
        test_obj = type_class.newInstance(name: cleaned_val)
        test_obj.validate()

      } catch (grails.validation.ValidationException ve) {
        ve.errors.fieldErrors?.each {
          if (it.code == 'notUnique') {
            result.result = 'ERROR'
            result.errors = [[message: 'A component with this name already exists!', messageCode: 'validation.nameNotUnique', value: value]]
          }
        }
      }

      test_obj?.discard()
    }
    else {
      result.result = 'ERROR'
      result.errors = [[message: 'Please provide a name!', messageCode: 'validation.missingName', value: value]]
    }

    result
  }
}
