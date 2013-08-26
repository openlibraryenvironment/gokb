modules = {
  application {
    resource url:'js/application.js.gsp'
    resource url:'js/bootstrap-editable.min.js'
  }
  editable {
    resource url:'css/select2.css'
    resource url:'js/editable.js.gsp'
    resource url:'js/moment.min.js'
    resource url:'js/select2.min.js'
  }
  overrides {
    'bootstrap-css' {
      resource id: 'bootstrap-css', url:'/css/gokbbootstrap.min.css'
      resource id: 'bootstrap-responsive-css', url:'/css/gokbbootstrap-responsive.min.css'
    }
  } 
}
