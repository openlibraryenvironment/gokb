modules = {
  gokbstyle {
    dependsOn 'application'
    dependsOn 'editable'
    resource url: 'css/style.css'
    // resource url: "css/${ApplicationHolder.application.config.defaultCssSkin?:'live.css'}"
  }
  
  gokbcharts {
    dependsOn 'gokbstyle'
    resource url: 'js/morris/raphael.min.js'
    resource url: 'js/morris/morris.min.js'
    resource url: 'css/morris.css'
  }
  
  
  overrides {
    'bootstrap-css' {
      resource id: 'bootstrap-css', url:"/css/${grailsApplication.config.defaultCssSkin?:'gokbbootstrap-3.min.css'}"
    }
  }
}
