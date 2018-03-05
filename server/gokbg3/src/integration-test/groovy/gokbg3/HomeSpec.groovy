package gokbg3

import grails.testing.mixin.integration.Integration
import grails.transaction.*

import geb.spock.*

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 * https://github.com/geb/geb/blob/master/doc/manual-snippets/src/test/groovy/navigator/FileUploadSpec.groovy
 */
@Integration
@Rollback
class HomeSpec extends GebSpec {

  def setup() {
  }

  def cleanup() {
  }


  void "Test public home page"() {
    when:"The home page is visited"
      go '/public'

    then:"The title is correct"
      title == "GOKb: Packages"
    true
  }
}
