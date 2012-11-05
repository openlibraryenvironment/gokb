package org.gokb

import grails.plugins.springsecurity.Secured
import grails.converters.JSON


class ApiController {

  def index() { 
  }
  
  @Secured(["ROLE_USER"])
  def describe() {

    def result = [
      rules: [
        [ name:'rule1', blurb:'blurb' ],
        [ name:'rule2', blurb:'blurb' ],
        [ name:'rule3', blurb:'blurb' ],
        [ name:'rule4', blurb:'blurb' ],
      ]
    ]

    render result as JSON
  }


  @Secured(["ROLE_USER"])
  def ingest() {

    def result = [
      ingestResult: [
        [ name:'rule1', blurb:'blurb' ],
        [ name:'rule2', blurb:'blurb' ],
        [ name:'rule3', blurb:'blurb' ],
        [ name:'rule4', blurb:'blurb' ],
      ]
    ]

    render result as JSON
  }

}
