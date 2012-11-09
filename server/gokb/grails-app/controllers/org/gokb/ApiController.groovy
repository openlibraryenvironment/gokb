package org.gokb

import grails.plugins.springsecurity.Secured
import grails.converters.JSON

import org.codehaus.groovy.grails.web.json.JSONArray;
import org.codehaus.groovy.grails.web.json.JSONObject
import org.gokb.refine.RefineOperation


class ApiController {
  
  // Internal API return object that ensures consistent formatting of API return objects
  private def apiReturn = {result, String message = "", String status = "success" ->
	  return [
		status		: (status),
		result		: (result),
		message		: (message),
      ]
  }
  
  // Helper to render the data as JSONP to allow cross-domain JSON.
  private void renderJSONP(data) {
    def json = data as JSON
	log.debug (json)
    render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
  }

  def index() { 
  }
  
//  @Secured(["ROLE_USER"])
  def describe() {
	 
	// Default operations to be supplied by the app.
	def operations = [
		"{\"description\":\"Rename column Title to Name\",\"operation\":{\"op\":\"core/column-rename\",\"description\":\"Rename column Title to Name\",\"oldColumnName\":\"Title\",\"newColumnName\":\"Name\"}}",
		"{\"description\":\"Text transform on 539 cells in column Electronic ISSN: grel:split(value,\'-\')[0]+ \\\"*\\\" + split(value,\'-\')[1]\",\"operation\":{\"op\":\"core/text-transform\",\"description\":\"Text transform on cells in column Electronic ISSN using expression grel:split(value,\'-\')[0]+ \\\"*\\\" + split(value,\'-\')[1]\",\"engineConfig\":{\"facets\":[],\"mode\":\"record-based\"},\"columnName\":\"Electronic ISSN\",\"expression\":\"grel:split(value,\'-\')[0]+ \\\"*\\\" + split(value,\'-\')[1]\",\"onError\":\"keep-original\",\"repeat\":false,\"repeatCount\":10}}",
		"{\"description\":\"Create new column Test based on column Frequency by filling 500 rows with grel:value+1\",\"operation\":{\"op\":\"core/column-addition\",\"description\":\"Create column Test at index 3 based on column Frequency using expression grel:value+1\",\"engineConfig\":{\"facets\":[],\"mode\":\"record-based\"},\"newColumnName\":\"Test\",\"columnInsertIndex\":3,\"baseColumnName\":\"Frequency\",\"expression\":\"grel:value+1\",\"onError\":\"set-to-blank\"}}"
	]
	
	// Return list of refine Operations
	def result = []
	operations.each {
		JSONObject json = JSON.parse(it)
		result.add(new RefineOperation(
			description : json['operation']['description'],
			operation : new HashMap(json['operation'])
		))
	}
	
	renderJSONP (apiReturn(result))
  }
  
  def saveOperations() {
	  log.debug(JSON.parse(request))
  }


  @Secured(["ROLE_USER"])
  def ingest() {

    def result = apiReturn (
      [
        [ name:'rule1', blurb:'blurb' ],
        [ name:'rule2', blurb:'blurb' ],
        [ name:'rule3', blurb:'blurb' ],
        [ name:'rule4', blurb:'blurb' ],
      ]
    )

    render result as JSON
  }
}
