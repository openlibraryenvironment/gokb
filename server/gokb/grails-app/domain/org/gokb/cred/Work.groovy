package org.gokb.cred

import javax.persistence.Transient
import org.gokb.GOKbTextUtils
import org.gokb.DomainClassExtender
import groovy.util.logging.*

@Log4j
class Work extends KBComponent {


  @Transient
  getInstances() {
    TitleInstance.executeQuery('select t from TitleInstance as t w where t.work = :work',[work:this]);
  }
}

