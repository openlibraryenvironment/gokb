package com.k_int.utils;

import org.gokb.cred.ContentItem
import org.springframework.context.support.AbstractMessageSource
import java.text.MessageFormat;


/**
 * @See https://stackoverflow.com/questions/8297505/grails-i18n-from-property-files-backed-up-by-a-db
 */
class DatabaseMessageSource extends AbstractMessageSource {

  def messageBundleMessageSource
  private Map message_cache = new java.util.HashMap();

  protected MessageFormat resolveCode(String code, Locale locale) {

    String cache_key = code+':'+locale.toString();

    MessageFormat format = message_cache.get(cache_key)

    if ( format == null ) {

      ContentItem.withTransaction {
        // Try to find by key and locale, otherwise, by key and blank locale
        ContentItem ci = ContentItem.findByKeyAndLocale(code, locale.toString()) ?: ContentItem.findByKeyAndLocale(code, 'default');

        if ( ci ) {
          format = new MessageFormat(ci.content, locale)

          // We only cache DB settings
          if ( format != null ) {
            message_cache.put(cache_key, format);
          }
        }
        else {
          if ( messageBundleMessageSource ) {
            try {
              format = messageBundleMessageSource?.resolveCode(code, locale)
            } 
            catch ( Exception e ) {
              // Something went badly wrong, return the code as the messge and carry on.
              // System.err.println("Problem trying to lookup message with key ${cache_key}");
              format = new MessageFormat(code, locale)
            }
          }
        }
      }

    }

    return format;
  }

  public void clearCache() {
    message_cache = new java.util.HashMap();
  }

  public void evictFromCache(String code, Locale locale) {
    String cache_key = code+':'+locale.toString();
    message_cache.remove(cache_key);
  }
}
