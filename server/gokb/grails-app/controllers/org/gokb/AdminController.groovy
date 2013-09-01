package org.gokb

class AdminController {

  def tidyOrgData() {
    redirect(url: request.getHeader('referer'))
  }
}
