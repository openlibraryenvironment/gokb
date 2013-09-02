package org.gokb

import grails.converters.JSON


class SearchBuilderController {

  def index() { 
  }

  def getClasses() {
    def result=[
      values:[
        [id:'1', text:'one'],
        [id:'2', text:'two'],
      ]
    ]

    render result as JSON
  }
}
