package gokbg3

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import org.springframework.beans.factory.annotation.*
import org.springframework.web.context.WebApplicationContext

import geb.spock.*


import geb.report.*

/**
 * See http://www.gebish.org/manual/current/ for more instructions
 * https://github.com/geb/geb/blob/master/doc/manual-snippets/src/test/groovy/navigator/FileUploadSpec.groovy
 */
@Integration
@Rollback
class HomeSpec extends GebSpec {

  @Autowired
  WebApplicationContext ctx

  // def reporter = new CompositeReporter(new PageSourceReporter(), new ScreenshotReporter(), new FramesSourceReporter())

  def setup() {
  }

  def cleanup() {
  }


  void "Test public home page"() {
    when:"The home page is visited"
      go '/public'
      browser.report("Public Home");

    then:"The title is correct"
      title == "GOKb: Packages"
    true
  }
}
