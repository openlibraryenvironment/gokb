package org.gokb.refine

import org.codehaus.groovy.grails.web.json.JSONObject;

class RefineOperation {
	
	static mapping = {
        operation type: "text"
    }

    String description
	JSONObject operation
}