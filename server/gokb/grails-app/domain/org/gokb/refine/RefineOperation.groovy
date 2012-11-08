package org.gokb.refine

import org.codehaus.groovy.grails.web.json.JSONObject;

class RefineOperation {
	
	/*
	 * Define how we handle the JSON sent to us from refine to create an operation here.
	 */
	RefineOperation (JSONObject json) {
		description = json['operation']['description']
		operation = new LinkedHashMap(json['operation'])
	}

	static hasMany = [operation : String]
	
	int id;
    String description
	Map operation = [:]
}