import com.k_int.UserPasswordEncoderListener
import com.k_int.utils.DatabaseMessageSource;
import org.grails.spring.context.support.PluginAwareResourceBundleMessageSource;
import org.springframework.web.servlet.i18n.SessionLocaleResolver

beans = {
  userPasswordEncoderListener(UserPasswordEncoderListener)

  messageBundleMessageSource(PluginAwareResourceBundleMessageSource) {
    basenames = ["WEB-INF/grails-app/i18n/messages"]
    defaultEncoding = "UTF-8"
    cacheSeconds = 10
    fileCacheSeconds = 10
    fallbackToSystemLocale = false
  }

  messageSource(DatabaseMessageSource) {
    messageBundleMessageSource = ref("messageBundleMessageSource")
  }

beans = {
    localeResolver(SessionLocaleResolver) {
        defaultLocale= new Locale('en')
    }
}

}
