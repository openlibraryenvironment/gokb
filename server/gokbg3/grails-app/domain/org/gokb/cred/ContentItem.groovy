package org.gokb.cred;

class ContentItem {

  String id
  String key
  String locale
  String content

  static mapping = {

         id column:'ci_id', generator: 'uuid', length:36
        key column:'ci_key'
     locale column:'ci_locale'
    content column:'ci_content', type:'text'
  }

  static constraints = {
        key(nullable:false, blank:false)
     locale(nullable:false, blank:true)
    content(nullable:false, blank:false)
  }

  static def lookupOrCreate(key,locale,content) {
    def result = ContentItem.findByKeyAndLocale(key,locale)
    if ( result == null ) {
      result = new ContentItem(key:key, locale:locale, content:content);
      result.locale = locale
      result.save()
    }
    result
  }
}
