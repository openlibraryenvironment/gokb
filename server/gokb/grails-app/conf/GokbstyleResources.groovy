modules = {
  gokbstyle {
    dependsOn 'bootstrap'
    dependsOn 'bootstrap-popover'
    resource url: 'css/style.css'
    resource url: 'css/bootstrap-editable.css'
    resource url: 'css/metisMenu.min.css'
    resource url: 'css/sb-admin-2.css'
    // resource url: "css/${ApplicationHolder.application.config.defaultCssSkin?:'live.css'}"
  }
}
