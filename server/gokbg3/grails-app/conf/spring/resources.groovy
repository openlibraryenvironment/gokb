import com.k_int.UserPasswordEncoderListener
import com.k_int.utils.DatabaseMessageSource;
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


}
