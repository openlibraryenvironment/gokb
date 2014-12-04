/*
        This is the Geb configuration file.
        See: http://www.gebish.org/manual/current/configuration.html
*/

import org.openqa.selenium.firefox.FirefoxDriver

driver = { new FirefoxDriver()}
reportsDir = "target/geb-reports"
atCheckWaiting = true


environments {

    // run as “grails -Dgeb.env=chrome test-app”
    // See: http://code.google.com/p/selenium/wiki/ChromeDriver
//    chrome {
  //      driver = { new ChromeDriver() }
    //}

    // run as “grails -Dgeb.env=firefox test-app”
    // See: http://code.google.com/p/selenium/wiki/FirefoxDriver
    firefox {
        driver = { new FirefoxDriver() }
    }
}

/*
phantomjs = { DesiredCapabilities caps = new DesiredCapabilities()
    caps.setCapability("takesScreenshot", true)
    caps.setCapability(PhantomJSDriverService.PHANTOMJS_EXECUTABLE_PATH_PROPERTY,
            "path/to/phantomjs.exe")
    def driver = new PhantomJSDriver(caps)
    driver.manage().window().setSize(new Dimension(1280, 768))
    driver
}
*/

