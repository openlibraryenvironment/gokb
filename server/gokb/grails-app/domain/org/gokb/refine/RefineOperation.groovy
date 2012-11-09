package org.gokb.refine

import org.codehaus.groovy.grails.web.json.JSONObject;

class RefineOperation {
	
	/*
	 * Define how we handle the JSON sent to us from refine to create an operation here.
	 */
	RefineOperation (JSONObject json) {
		
		// Ignore the description property at root level as that contains some document
		// specific information like number of rows affected and instead use the
		// more generic operation.description property.  
		description = json['operation']['description']
		operation = new LinkedHashMap(json['operation'])
	}

	static hasMany = [operation : String]
	
	int id;
    String description
	Map operation = [:]
}