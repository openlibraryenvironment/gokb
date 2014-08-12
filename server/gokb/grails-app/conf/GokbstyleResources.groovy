modules = {
  gokbstyle {
    dependsOn 'application'
    resource url: 'css/style.css'
    // resource url: "css/${ApplicationHolder.application.config.defaultCssSkin?:'live.css'}"
  }
}
