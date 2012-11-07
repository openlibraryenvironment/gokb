package org.gokb

import grails.plugins.springsecurity.Secured
import grails.converters.JSON


class ApiController {
  // Internal API return object that ensures consistent formatting of API return objects
  def apiReturn = {result, String status = "success" ->
	  return [
		status : (status),
		result : (result),
      ]
  }

  def index() { 
  }
  
//  @Secured(["ROLE_USER"])
  def describe() {

	log.debug((params as JSON))
	
    def result = apiReturn ( 
      [
        [ name:'rule1', description:'blurb' ],
        [ name:'rule2', description:'blurb' ],
        [ name:'rule3', description:'blurb' ],
        [ name:'rule4', description:'blurb' ],
      ]
    )
	
	renderJSONP (result)
  }
  
  // Helper to render the data as JSONP to allow cross-domain JSON.
  void renderJSONP(data) {
    def json = data as JSON
    render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
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
