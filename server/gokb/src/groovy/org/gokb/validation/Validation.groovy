package org.gokb.validation

import groovy.util.logging.Log4j;

@Log4j
class Validation {

  private static final Map<String, ValidationRule> validationRules = [:]
  
  public static final String CONTEXT_COLUMN = "context-column"
  public static final String CONTEXT_ROW = "context-row"
  
  public static addRule (ValidationRule rule, String context) {
	
	// Get the rules.
	List rules = validationRules[context]
	
	// None added to this context yet.
	if (rules == null) {
	  
	  // Add the context list and default to empty list.
	  rules = []
	  validationRules[context] = rules
	}
	
	// Add the rule to the list.
	rules << rule
  }
  
  public static Map getRules () {
	validationRules
  }
  
  public static boolean validate (def project_data) {
	
	// Define the object to contain the results of the validation routine.
	def result = [:]
	result.status = true
	result.messages = []
	
	// Check processing complete
	checkProcessingComplete (result, project_data)
	
	// Create map containing the column positions.
	def col_positions = [:]
	project_data.columnDefinitions?.each { cd ->
	  log.debug("Assigning col ${cd.name} to position ${cd.cellIndex}");
	  col_positions[cd.name?.toLowerCase()] = cd.cellIndex;
	}
	
	// Check the rules for the column context.
	checkColumnRules (result, col_positions)
	
  }
  
  private void checkColumnRules (result, col_positions) {
	
	// The rules.
	List<ValidationRule> validationRules = validationRules [CONTEXT_COLUMN]
	
	// Execute each rule, passing in the column positions for each.
	validationRules.each {ValidationRule rule ->
	  project_data.status = project_data.status && rule.valid(col_positions)
	}
  }
  
  private void checkProcessingComplete(result, project_data) {
	if ( project_data?.processingCompleted ) {
	  log.debug("Processing of ingest file completed ok, validating")
	}
	else {
	  log.debug("Processing of ingest file failed, unable to vlidate.")
	  result.messages.add([text:'Unable to process ingest file at this time'])
	  result.status = false
	}
  }
}
