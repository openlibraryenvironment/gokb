package org.gokb.validation.types

import groovy.json.JsonOutput;

import org.gokb.cred.KBComponent
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import org.joda.time.format.*
import org.apache.taglibs.standard.tag.common.fmt.FormatDateSupport;
import org.codehaus.groovy.grails.web.context.ServletContextHolder as SCH
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes as GA

class CompareToTiDateField extends A_ValidationRule implements I_DeferredRowValidationRule {

  private static final String ERROR_TYPE = "ti_date_invalid"
  public static final String GTE = "gte"
  public static final String GT = "gt"
  public static final String LT = "lt"
  public static final String LTE = "lte"

  
  private final String ti_field_name
  private final String operator
  private final Map<String,String> class_one_cols
  
  private final def titleLookupService
  private static final DateTimeFormatter ISODateParser = ISODateTimeFormat.dateTimeParser()
  private static final DateTimeFormatter ISODatePrinter = ISODateTimeFormat.dateTime()  
  public CompareToTiDateField(String columnName, String severity, Map<String,String> class_one_cols, String ti_field_name, String operator) {
    super(columnName, severity)
    this.ti_field_name = ti_field_name
    this.operator = operator
    this.class_one_cols = class_one_cols
    
    def appContext = SCH.servletContext.getAttribute(GA.APPLICATION_CONTEXT)
    this.titleLookupService = appContext."titleLookupService"

    if (!(severity && class_one_cols && ti_field_name && operator)) {
      throw new IllegalArgumentException ("CompareToTiDateField rule expects ags: String severity, Map<String,String> class_one_cols, String ti_field_name, String operator.")
    }
  }
  
  private Date parseDate (String iso_string) {
    // Parse the date.
    Date the_date = null

    if (iso_string && iso_string.trim() != "") {
      try {
        the_date = ISODateParser.parseLocalDateTime(iso_string).toDate()

      } catch (Throwable t) {

        // Ensure null date.
        the_date = null
      }
    }

    the_date
  }
  
  private String formatDate ( Date the_date ) {
    String iso_string = null
    DateTimeZone g;
    if (the_date) {
      DateTime dt = new DateTime(the_date, DateTimeZone.UTC)
      iso_string = ISODatePrinter.print(dt)
    }
    
    iso_string
  }  

  @Override
  protected String getType() {

    // Return the type to be sent with each error message.
    return ERROR_TYPE;
  }

  @Override
  protected Map getMessageProperties() {
    String facetName = ""
    String message = "One or more rows contains values in \"${columnName}\" that "
    switch (operator) {
      case GTE :
        facetName = "pre-date"
        message += facetName
      break;
      case GT :
        facetName = "equal or pre-date"
        message += "either ${facetName}"
      break;
      case LTE :
        facetName = "post-date"
        message += facetName
      break;
      case LT :
        facetName = "equal or post-date"
        message += "either ${facetName}"
      break;
    }
    
    message += " the matched title ${ti_field_name}"
    
    // List of statements to set the broken values to the values from the title.
    def quick_fix = []
    
    // Facet string.
    String facet_string = ""
    
    // Grab each invalid option.
    invalid.eachWithIndex { def row, def row_num ->
      // Create the facet string.
      String row_entry
      String quick_fix_value
      row.eachWithIndex { def entry, def index ->
        
        if (index > 0) {
          String val = entry.value?.trim()
          if (val && val != "") {
            row_entry = "and( if (isNonBlank(cells[gokbCaseInsensitiveCellLookup('${entry.col_name}')]) , cells[gokbCaseInsensitiveCellLookup('${entry.col_name}')].value=='${entry.value}', false) , ${row_entry} )"
          } else {
            row_entry = "and( if (isNonBlank( cells[gokbCaseInsensitiveCellLookup('${entry.col_name}')], isBlank( cells[gokbCaseInsensitiveCellLookup('${entry.col_name}')], false ) , ${row_entry} )"
          }
        } else {
          row_entry = "if ( isNonBlank(cells[gokbCaseInsensitiveCellLookup('${entry.col_name}')]), cells[gokbCaseInsensitiveCellLookup('${entry.col_name}')].value==toDate('${entry.value}'), false )"
          
          // First entry contains the extra details we need.
          quick_fix_value = entry.ti_field_value
        }
      }
      
      // Let's add the quickfix string for the built facet string.
      quick_fix << "if ( ${row_entry}, '${quick_fix_value}'.toDate(), value)"
      
      // We need to add all to an or.
      if (row_num > 0) {
        facet_string = "or( ${row_entry}, ${facet_string} )"
      } else {
        facet_string = row_entry
      }
    }

    // The extra info to be sent with each error message.
    return [
      'compared_field'  : ti_field_name,
      'col'             : columnName,
      'text'            : message,
      'facetValue'      : facet_string,
      'facetName'       : "${facetName} TI",
      'transformations' : quick_fix
    ];
  }

  @Override
  public boolean validate(final result) {

    // Just check the list.
    if (invalid.size() > 0) {

      // Add the error.
      addError(result)
      return false
    }

    return true
  }

  private def invalid = []
  private def id_pos = null

  @Override
  public void process(final col_positions, final rowNum, final datarow, final reconData) {

    // First check should be to see if an error has already been triggered by this rule,
    // we don't want to fill the error messages with repeats.
    if (!isErrorTriggered()) {
      
      // We also need the indexes for any class one ids in the project
      if (id_pos == null) {
        
        // Default to empty map.
        id_pos = [:]
        class_one_cols.each { def name, def col_name ->
          if (name) {
            def p = col_positions[col_name]
            if (p != null) {
              id_pos[name] = [
                "col" : col_name,
                "pos" : p
              ]
            }
          }
        }
      }

      // Get the index for the column.
      def pos = col_positions[columnName]

      // Only check the content if the data is present in the first place.
      if (pos != null && id_pos.size() > 0) {

        // Get the value.
        String raw_val = getRowValue(datarow, col_positions, columnName)
        
        if ( (raw_val != null) && ( raw_val.length() > 0 ) ) {

          // Build a list of maps of namespace and value.
          def id_maps = []
          id_pos.each { def ns, def col_def ->
            
            // Grab the value.
            String row_val = jsonv(datarow.cells[col_def.pos]);
            if (row_val) {
              // Add an entry.
              id_maps << [
                "col_name" : col_def.col,
                "ns" : ns,
                "value" : row_val
              ]
            }
          }
          
          // Now lets find the titles.
          def results = titleLookupService.matchClassOnes(id_maps)

          // Default valid to true.
          boolean valid = true
          Date ti_date
          if (results.size() == 1) {
            
            // We can only reliably check if there is only one TI ID'd by all the identifiers.
            ti_date = results[0]."${ti_field_name}"
            
            if (ti_date) {
              Date date_val = parseDate( raw_val )
              
              if (date_val) {
                
                // Compare the 2 dates.
                int compare = date_val.compareTo(ti_date)
                switch (operator) {
                  case GTE :
                    valid = (compare >= 0)
                  break;
                  case GT :
                    valid = (compare > 0)
                  break;
                  case LTE :
                    valid = (compare <= 0)
                  break;
                  case LT :
                    valid = (compare < 0)
                  break;
                }
              }
            }
          }
          
          // Add data that can be used to construct a facet.
          if (!valid) {
            
            // Add the ID map plus also construct an entry for the
            // Value in the compared column too.
            def conditions = []
            conditions.add([
              "col_name"        : columnName,
              "value"           : raw_val,
              "ti_field_value"  : formatDate( ti_date )
            ])
            conditions.addAll(id_maps)
            
            // Add the conditions.
            invalid.add(conditions)
          }
        }
      }
    }
  }
}
