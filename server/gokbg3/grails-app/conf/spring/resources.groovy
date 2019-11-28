import com.k_int.UserPasswordEncoderListener
import com.k_int.utils.DatabaseMessageSource;
import org.grails.spring.context.support.PluginAwareResourceBundleMessageSource;
import grails.rest.render.hal.*


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

  halPackageRenderer(HalJsonRenderer, org.gokb.cred.Package)
  halBookInstanceRenderer(HalJsonRenderer, org.gokb.cred.BookInstance)

}
