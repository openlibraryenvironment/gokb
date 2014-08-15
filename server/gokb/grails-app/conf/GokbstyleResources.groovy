modules = {
  gokbstyle {
    dependsOn 'application'
    dependsOn 'editable'
    resource url: 'css/style.css'
    // resource url: "css/${ApplicationHolder.application.config.defaultCssSkin?:'live.css'}"
  }
  
  
  overrides {
    'bootstrap-css' {
      // resource id: 'bootstrap-css', url:'/css/gokbbootstrap-3.test.css'
      resource id: 'bootstrap-css', url:"/css/${grailsApplication.config.defaultCssSkin?:'gokbbootstrap-3.min.css'}"
      // resource id: 'bootstrap-css', url:'/css/gokbbootstrap-3.min.css'
      // resource id: 'bootstrap-responsive-css', url:'/css/gokbbootstrap-2-responsive.min.css'
    }
  }
}
