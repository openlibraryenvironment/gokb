package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

@Log4j
class BookInstance extends TitleInstance {

  String editionNumber
  String editionDifferentiator
  String editionStatement
  String volumeNumber
  Date dateFirstInPrint
  Date dateFirstOnline
  String summaryOfContent

 static mapping = {
            editionNumber column:'bk_ednum'
    editionDifferentiator column:'bk_editionDifferentiator'
         editionStatement column:'bk_editionStatement'
             volumeNumber column:'bk_volume'
         dateFirstInPrint column:'bk_dateFirstInPrint'
          dateFirstOnline column:'bk_dateFirstOnline'
         summaryOfContent column:'bk_summaryOfContent'
  }

  static constraints = {
            editionNumber (nullable:true, blank:false)
    editionDifferentiator (nullable:true, blank:false)
         editionStatement (nullable:true, blank:false)
             volumeNumber (nullable:true, blank:false)
         dateFirstInPrint (nullable:true, blank:false)
          dateFirstOnline (nullable:true, blank:false)
         summaryOfContent (nullable:true, blank:false)
  }

}
