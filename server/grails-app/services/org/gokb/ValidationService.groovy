package org.gokb


import com.github.ladutsko.isbn.*
import com.opencsv.CSVReader
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVParser
import com.opencsv.CSVParserBuilder
import grails.validation.ValidationException
import java.time.LocalDate
import org.apache.commons.io.ByteOrderMark
import org.apache.commons.io.input.BOMInputStream
import org.apache.commons.validator.routines.ISSNValidator
import org.apache.commons.validator.routines.UrlValidator

import org.gokb.cred.*
import org.gokb.GOKbTextUtils

class ValidationService {

  def TSVIngestionService
  def dateFormatService

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
        maxLength: 255,
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
        maxLength: 255,
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
        maxLength: 255,
        pubType: "Serial"
    ],
    num_first_issue_online: [
        mandatory: false,
        maxLength: 255,
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
        maxLength: 255,
        pubType: "Serial",
    ],
    num_last_issue_online: [
        mandatory: false,
        maxLength: 255,
        pubType: "Serial"
    ],
    title_url: [
        mandatory: true,
        maxLength: 1023,
        validator: [
            name: "checkUrl",
            args: []
        ]
    ],
    first_author: [
        mandatory: false,
        maxLength: 255
    ],
    title_id: [
        mandatory: false,
        maxLength: 255
    ],
    embargo_info: [
        mandatory: false,
        maxLength: 255,
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
        mandatory: false,
        maxLength: 255
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
        maxLength: 255,
        pubType: "Monograph"
    ],
    monograph_edition: [
        mandatory: false,
        maxLength: 255,
        pubType: "Monograph"
    ],
    first_editor: [
        mandatory: false,
        maxLength: 255,
        pubType: "Monograph"
    ],
    parent_publication_title_id: [
        mandatory: false,
        maxLength: 255
    ],
    preceding_publication_title_id: [
        mandatory: false,
        maxLength: 255
    ],
    access_type: [
        mandatory: true,
        strictOnly: true,
        validator: [
            name: "checkAccessType",
            args: []
        ]
    ],
    zdb_id: [
        mandatory: false,
        pubType: "Serial",
        namespaces: [
            'Serial': 'zdb',
        ],
        validator: [
            name: "checkKbartIdentifier",
            args: ["_colName", "publication_type"]
        ]
    ],
    ddc: [
        mandatory: false,
        validator: [
          name: "checkDDCList",
          args: []
        ]
    ]
  ]

  static final String[] MANDATORY_COLS = [
      'publication_title',
      'print_identifier',
      'online_identifier',
      'title_url',
      'publication_type'
  ]

  static final String[] PROPRIETARY_COLS = [
      'zdb_id',
      'ddc',
      'series'
  ]

  static ISSNValidator ISSN_VAL = new ISSNValidator()

  def generateKbartReport(InputStream kbart, IdentifierNamespace titleIdNamespace = null, boolean strict = false) {
    def result = [
        valid: true,
        message: "",
        rows: [
            total: 0,
            error: 0,
            warning: 0,
            skipped: 0
        ],
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
      if (info.mandatory && !header.contains(colName) && (!info.strictOnly || strict)) {
        result.errors.missingColumns.add(colName)
        result.valid = false
      }
      else if (!header.contains(colName) && !PROPRIETARY_COLS.contains(colName)) {
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
        else if (nl.size() >= MANDATORY_COLS.size()) {
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
          result.rows.skipped++

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

      if (!hasValidLength(trimmed_val, key)) {
        if (strict) {
          result.warnings[key] = [
              message    : "The value '${trimmed_val}' is unusually long for this column.",
              messageCode: "kbart.errors.longVal",
              args       : [trimmed_val]
          ]
        } else {
          result.errors[key] = [
              message    : "The value '${trimmed_val}' is unusually long for this column.",
              messageCode: "kbart.errors.longVal",
              args       : [trimmed_val]
          ]
        }
      }

      if (trimmed_val?.contains('�') && !result.errors["replacementChars"]) {
        result.errors["replacementChars"] = [
            message: "Value contains UTF-8 replacement characters!",
            messageCode: "kbart.errors.replacementCharsRow",
            args: []
        ]
      }
      else if (trimmed_val?.contains('¶') ||
          trimmed_val?.contains('¦') ||
          trimmed_val?.contains('¤') ||
          trimmed_val ==~ /\p{Cc}/ ||
          trimmed_val?.contains('Ãƒ')
      ) {
        result.warnings[key] = [
            message: "Value '${trimmed_val}' contains unusual characters.",
            messageCode: "kbart.errors.unusualCharsVal",
            args: [trimmed_val]
        ]
      }

      if (KNOWN_COLUMNS[key]) {
        if (KNOWN_COLUMNS[key].mandatory && !trimmed_val && (strict || !KNOWN_COLUMNS[key].strictOnly)) {
          result.errors[key] = [
              message: "Missing value in mandatory column '${key}'",
              messageCode: "kbart.errors.missingVal",
              args: [key]
          ]
        }
        else if (key == 'title_id' && !trimmed_val) {
          result.warnings[key] = [
              message: "This line does not contain a value for the common title id!",
              messageCode: "kbart.errors.noTitleId",
              args: []
          ]
        }
        else if (key == 'title_id' && titleIdNamespace) {
          def field_valid_result = checkIdForNamespace(trimmed_val, titleIdNamespace)

          if (!field_valid_result) {
            def nslabel = (titleIdNamespace.name ?: titleIdNamespace.value)

            result.errors[key] = [
                message: "Identifier value '${trimmed_val}' for namespace '$nslabel' is not valid!",
                messageCode: "kbart.errors.illegalValForNamespace",
                args: [trimmed_val, nslabel]
            ]
          }
        }
        else if (!pubType && (key == 'online_identifier' || key == 'print_identifier')) {
          log.debug("Skipping ID columns due to missing publication_type")
        }
        else if (KNOWN_COLUMNS[key].validator && trimmed_val) {
          def final_args = [trimmed_val] + KNOWN_COLUMNS[key].validator.args?.collect { it == "_colName" ? key : nl[col_positions[it]] }
          def field_valid_result = "${KNOWN_COLUMNS[key].validator.name}"(*final_args)

          if (field_valid_result instanceof Map) {
            if (field_valid_result.result == 'ERROR') {
              result.errors[key] = field_valid_result.errors
            }
            else if (field_valid_result.result == 'WARNING') {
              result.warnings[key] = field_valid_result.warnings
            }
          }
          else if (!field_valid_result || (strict && field_valid_result != trimmed_val && key != 'publication_title')) {
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

    if (!col_positions['online_identifier'] && !col_positions['print_identifier']) {
      if (!col_positions['title_id']) {
        result.errors["noIds"] = [
            message: "This row contains no usable identifiers!",
            messageCode: "kbart.errors.noIds",
            args: []
        ]
      }
      else if (!strict && !titleIdNamespace && (pubType != 'Serial' || !col_positions['zdb_id'])) {
        result.errors["noIds"] = [
            message: "This row contains no usable identifiers and will not be processed!",
            messageCode: "kbart.errors.noIds",
            args: []
        ]
      }
      else {
        result.warnings["noIds"] = [
            message: "This row contains neither a 'print_identifier', nor an 'online identifier'!",
            messageCode: "kbart.errors.noAuthIds",
            args: []
        ]
      }
    }

    if (col_positions['date_first_issue_online'] && col_positions['date_last_issue_online']) {
      def date_order = checkDatePair(
          nl[col_positions['date_first_issue_online']],
          nl[col_positions['date_last_issue_online']])

      if (date_order == 'error') {
        result.errors['date_last_issue_online'] = [
            message: "The end date must be after the start date.",
            messageCode: "validation.dateRange",
            args: []
        ]
      }
    }

    def coverageCheck = checkCoverageRange(col_positions['num_first_vol_online'],
        col_positions['num_first_issue_online'],
        col_positions['num_last_vol_online'],
        col_positions['num_last_issue_online'])

    if (!coverageCheck.valid) {
      result.errors << coverageCheck.errors
    }

    result
  }

  boolean hasValidLength(String trimmed_val, String column) {
    boolean result = true

    if (trimmed_val?.length() > 1023 ||
        (KNOWN_COLUMNS[column] &&
         KNOWN_COLUMNS[column].maxLength &&
         trimmed_val.length() > KNOWN_COLUMNS[column].maxLength)
    ) {
      result = false
    }

    result
  }

  def checkEmbargoCode(String value) {
    return (value ==~ ~"^(([RP][1-9][0-9]*[DMY])|(R[1-9][0-9]*[DMY];P[1-9][0-9]*[DMY]))\$" ? value : false)
  }

  def checkCoverageRange(startVolume, startIssue, endVolume, endIssue) {
    def result = [valid: true, errors: []]

    if ((startVolume instanceof Integer || startVolume?.isInteger()) &&
        (endVolume instanceof Integer || endVolume?.isInteger())
    ) {
      if (startVolume > endVolume) {
        result.valid = false
        result.errors << [
            'num_first_vol_online': [
                message: "The start volume is greater than the end volume!",
                messageCode: "validation.volumeRange"
            ]
        ]
      }
      else if (startVolume as int == endVolume as int) {
        if ((startIssue instanceof Integer || startIssue?.isInteger()) &&
            (endIssue instanceof Integer || endIssue?.isInteger())
        ) {
          if (startIssue > endIssue) {
            result.valid = false
            result.errors << [
                'num_first_issue_online': [
                    message: "The start issue for is greater than the last issue!",
                    messageCode: "validation.issueRange"
                ]
            ]
          }
        }
      }
      else if (!startIssue && endIssue) {
        result.valid = false
        result.errors << [
            'num_last_issue_online': [
                message: "Coverage has a last issue but no first issue!",
                messageCode: "validation.missingStartIssue"
            ]
        ]
      }
    }
    else if (!startVolume && endVolume) {
      result.valid = false
      result.errors << [
          'num_last_vol_online': [
              message: "Coverage has a last volume but no first volume!",
              messageCode: "validation.missingStartVolume"
          ]
      ]
    }

    result
  }

  def checkPubType(String value) {
    String result

    RefdataValue.withNewSession {
      RefdataValue resolvedType = RefdataCategory.lookup('TitleInstancePackagePlatform.PublicationType', value.trim())

      if (resolvedType) {
        result = resolvedType.value
      }
    }

    result
  }

  def checkAccessType(String value) {
    String final_val

    if (value in ['F', 'f']) {
      final_val = 'F'
    }
    else {
      final_val = 'P'
    }

    final_val
  }

  def checkCoverageDepth(String value) {
    String result
    String final_val = value

    if (value?.toLowerCase()?.trim() in ['full text', 'volltext']) {
      final_val = 'fulltext'
    }

    RefdataValue.withNewSession {
      RefdataValue resolvedType = RefdataCategory.lookup('TIPPCoverageStatement.CoverageDepth', final_val.trim())

      if (resolvedType) {
        result = value
      }
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
      IdentifierNamespace.withNewSession {
        def namespace = IdentifierNamespace.findByValue(KNOWN_COLUMNS[column]?.namespaces?."${final_type}")
        result = checkIdForNamespace(value.trim(), namespace)
      }
    }

    result
  }

  def checkIdForNamespace(String value, IdentifierNamespace titleIdNamespace) {
    def result = null

    if (titleIdNamespace.value in ['isbn', 'pisbn']) {
      try {
        def valid_isbn = ISBN.parseIsbn(value)

        result = value

        if (ISBN.isIsbn10(value)) {
          result = valid_isbn.getIsbn13()
        }
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

      if (checkDigit == 10 && parts[1] in ['x', 'X'] ||
          (parts[1] in ['0','1', '2', '3', '4', '5', '6', '7', '8', '9'] && checkDigit == Integer.valueOf(parts[1]))
      ) {
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

  def checkTimestamp(String value, def format = null) {
    def result = null

    if (!format || format == 'sec') {
      try {
        result = dateFormatService.parseTimestamp(value) ? value : null
      }
      catch (Exception e) {}
    }

    if (!result && (!format || format == 'ms')) {
      try {
        result = dateFormatService.parseTimestampMs(value) ? value : null
      }
      catch (Exception e) {}
    }

    if (!result && (!format || format == 'iso')) {
      try {
        result = dateFormatService.parseIsoMsTimestamp(value) ? value : null
      }
      catch (Exception e) {}
    }

    result
  }

  def checkUrl(String value, boolean replaceDate = false) {
    String local_date_string = LocalDate.now().toString()

    def final_val = value.trim()

    if (replaceDate) {
      final_val = final_val.replace('{YYYY-MM-DD}', local_date_string)
    }

    if (final_val.indexOf('%') >= 0 || replaceDate) {
      // log.debug("URL seems to be already encoded!")
    }
    else {
      String url = ""
      def parts = null

      if (parts = final_val =~ /^((?>http[s]?|ftp):\/\/)(\w[\w\-\.]+)(\/[\w\-\/]+\/)*(\/)?([^#]+)?(#[\w\-]+)?$/) {
        for (int i = 1; i < parts.groupCount(); i++) {
          if (i != 5 && parts.group(i)) {
            url = url + parts.group(i)
          }
          else if (parts.group(i)) {
            try {
              url = url + URLEncoder.encode(parts.group(i))
            }
            catch(Exception e) {
              // log.debug("Invalid query part ${parts.group(i)}")
            }
          }
        }
        final_val = url
      }
      else {
        // log.debug("Regex fail for URL: ${final_val}")
      }
    }

    // log.debug("Final URL to check: ${final_val}")

    return new UrlValidator().isValid(final_val) ? value : null
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
      result.errors = [
        [
          message: 'Unknown component type!',
          messageCode: 'validation.unknownType',
          value: componentType
        ]
      ]

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

      } catch (ValidationException ve) {
        ve.errors.fieldErrors?.each {
          if (it.code == 'notUnique') {
            result.result = 'ERROR'
            result.errors = [
              [
                message: 'A component with this name already exists!',
                messageCode: 'validation.nameNotUnique',
                value: value
              ]
            ]
          }
        }
      }

      test_obj?.discard()
    }
    else {
      result.result = 'ERROR'
      result.errors = [
        [
          message: 'Please provide a name!',
          messageCode: 'validation.missingName',
          value: value
        ]
      ]
    }

    result
  }

  def checkSubject(RefdataValue scheme, String value) {
    def result = [result: 'OK']
    RefdataCategory scheme_category = RefdataCategory.findByDesc('Subject.Scheme')

    if (scheme.owner == scheme_category) {
      if (scheme.value == 'DDC') {
        def notation_result = checkDDCNotation(value)

        if (notation_result.result == 'ERROR') {
          result = notation_result
          result.baddata = [
            scheme: [
              id: scheme.id,
              value: scheme.value,
              type: 'RefdataValue'
            ],
            heading: value
          ]
        }
      }
    }
    else {
      result.result = 'ERROR'
      result.errors = [
        [
          message: "Unable to reference subject scheme.",
          messageCode: "validation.subject.scheme.notFound",
          value: scheme
        ]
      ]
    }
  }

  def checkDDCList(String value) {
    def result = [result: 'OK']
    def notations = value?.trim()?.split(';') ?: []

    if (notations.size() > 0) {
      notations.each {
        def validation_result = checkDDCNotation(it)

        if (validation_result.result == 'ERROR') {
          result.result = 'ERROR'
          result.errors = result.errors ? result.errors + validation_result.errors : validation_result.errors
        }
      }
    }

    result
  }

  def checkDDCNotation(String notation) {
    def result = [result: 'OK']

    if (notation ==~ /^\d{3}$/) {
      log.debug("Valid DDC notation!")
    }
    else if (notation ==~ /^\d{3}.\d*$/) {
      result.result = 'ERROR'
      result.errors = [
        [
          message: "Deep DDC notations like '${notation}' are not supported.",
          messageCode: "component.subject.ddc.error.longNotation",
          value: notation
        ]
      ]
    }
    else {
      result.result = 'ERROR'
      result.errors = [
        message: "Value '${notation}' is not a valid DDC notation.",
        messageCode: "component.subject.ddc.error.notationFormat",
        value: notation
      ]
    }

    result
  }
}
