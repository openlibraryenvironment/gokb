import com.k_int.UserPasswordEncoderListener
import com.k_int.utils.DatabaseMessageSource
import grails.rest.render.hal.HalJsonRenderer
import org.gokb.cred.BookInstance
import org.gokb.cred.Package;
import org.grails.spring.context.support.PluginAwareResourceBundleMessageSource;


beans = {
  userPasswordEncoderListener(UserPasswordEncoderListener)

  messageBundleMessageSource(PluginAwareResourceBundleMessageSource) {
    basenames = "WEB-INF/grails-app/i18n/messages"
    cacheSeconds = 10
    fileCacheSeconds = 10
  }

  messageSource(DatabaseMessageSource) {
    messageBundleMessageSource = ref("messageBundleMessageSource")
  }

  halPackageRenderer(HalJsonRenderer, Package)
  halBookInstanceRenderer(HalJsonRenderer, BookInstance)

}
