package org.gokb


class HomeController {

  def grailsApplication

  def index() { }

  def showRules() {
    def result=[:]
    result.rules = grailsApplication.config.validationRules
    result
  }
}
