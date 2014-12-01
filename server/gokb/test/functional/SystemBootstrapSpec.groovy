import geb.error.RequiredPageContentNotPresent
import geb.spock.GebReportingSpec
import pages.*
import spock.lang.Stepwise

// Take an empty GOKb system and prepopulate it with enough info to do some useful tests

@Stepwise
class SystemBootstrapSpec extends GebReportingSpec {

  def "FrontPage" (){
    when:
      go "http://localhost:80080/gokb"
      // to PublicPage
      // at PublicPage
    then:
      browser.page.title.startsWith "GOKb: Welcome"
  }
 
}
