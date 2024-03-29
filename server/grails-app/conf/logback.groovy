import grails.util.BuildSettings
import grails.util.Environment
import org.springframework.boot.logging.logback.ColorConverter
import org.springframework.boot.logging.logback.WhitespaceThrowableProxyConverter

import java.nio.charset.Charset

conversionRule 'clr', ColorConverter
conversionRule 'wex', WhitespaceThrowableProxyConverter

// See http://logback.qos.ch/manual/groovy.html for details on configuration
appender('STDOUT', ConsoleAppender) {
  encoder(PatternLayoutEncoder) {
    charset = Charset.forName('UTF-8')

    pattern =
      '%clr(%d{yyyy-MM-dd HH:mm:ss.SSS}){faint} ' + // Date
        '%clr(%5p) ' + // Log level
        '%clr(---){faint} %clr([%15.15t]){faint} ' + // Thread
        '%clr(%-40.40logger{39}){cyan} %clr(:){faint} ' + // Logger
        '%m%n%wex' // Message
  }
}

if (Environment.isDevelopmentMode() || Environment.current == Environment.TEST) {
  logger('com.k_int', DEBUG)
  logger('org.gokb', DEBUG)
  logger('gokbg3', INFO)

  logger('org.gokb.DomainClassExtender', WARN)
  logger('org.gokb.ClassExaminationService', WARN)
  logger('org.gokb.IntegrationController', DEBUG)
  logger('org.gokb.CrossReferenceService', ERROR)
  logger('org.gokb.CrossRefPkgRun', DEBUG)
  logger('org.gokb.UpdatePkgTippsRun', DEBUG)
  logger('org.gokb.PackageService', DEBUG)
  logger('org.gokb.TippService', DEBUG)
  logger('org.gokb.CleanupService', DEBUG)
  logger('org.gokb.ComponentLookupService', INFO)
  logger('org.gokb.ComponentStatisticService', INFO)
  logger('org.gokb.ComponentUpdateService', INFO)
  logger('org.gokb.TitleLookupService', DEBUG)
  logger('org.gokb.PackageSourceUpdateService', DEBUG)
  logger('org.gokb.TSVIngestionService', DEBUG)
  logger('org.gokb.SearchController', INFO)
  logger('org.gokb.ResourceController', ERROR)
  logger('org.gokb.AutoUpdatePackagesJob', DEBUG)
  logger('org.gokb.AugmentJob', DEBUG)
  logger('org.gokb.TippMatchingJob', DEBUG)
  logger('org.gokb.FTUpdateService', ERROR)
  logger('org.gokb.ESUpdateJob', ERROR)
  logger('org.gokb.cred', ERROR)
  logger('org.gokb.cred.BookInstance', ERROR)
  logger('org.gokb.cred.JournalInstance', ERROR)
  logger('org.gokb.cred.RefdataCategory', ERROR)
  logger('org.gokb.cred.Package', DEBUG)
  logger('org.gokb.cred.TitleInstancePackagePlatform', DEBUG)
  logger('gokbg3.UserDetailsInterceptor', ERROR)
  logger('gokbg3.PreferencesInterceptor', ERROR)
  logger('com.k_int.ESSearchService', INFO)
}
else {
  logger('com.k_int', INFO)
  logger('org.gokb', INFO)
  logger('gokbg3', INFO)
}

def targetDir = BuildSettings.TARGET_DIR
if (Environment.isDevelopmentMode() && targetDir != null) {
  appender("FULL_STACKTRACE", FileAppender) {
    file = "${targetDir}/stacktrace.log"
    append = true
    encoder(PatternLayoutEncoder) {
      pattern = "%level %logger - %msg%n"
    }
  }
  logger("StackTrace", ERROR, ['FULL_STACKTRACE'], false)
}
root(ERROR, ['STDOUT'])
