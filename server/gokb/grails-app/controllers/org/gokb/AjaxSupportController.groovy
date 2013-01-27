package org.gokb

import grails.converters.JSON

class AjaxSupportController {

  def edit() { 
    log.debug("edit ${params}");
    def result = [:]
    render result as JSON
  }
}
