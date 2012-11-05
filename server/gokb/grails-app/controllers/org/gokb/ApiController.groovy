package org.gokb

import grails.plugins.springsecurity.Secured
import grails.converters.JSON


class ApiController {
  // Internal API return object that 
  def apiReturn = {result, String code = "success" ->
	  return [
		code : (code),
		result : (result),
      ]
  }

  def index() { 
  }
  
//  @Secured(["ROLE_USER"])
  def describe() {

	log.debug(params)
	  
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
  
  def renderJSONP(data) {
    def json = data as JSON
    render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
  }


  @Secured(["ROLE_USER"])
  def ingest() {

    def result = apiReturn (
      ingestResult: [
        [ name:'rule1', blurb:'blurb' ],
        [ name:'rule2', blurb:'blurb' ],
        [ name:'rule3', blurb:'blurb' ],
        [ name:'rule4', blurb:'blurb' ],
      ]
    )

    render result as JSON
  }
}
