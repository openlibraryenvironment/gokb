modules = {
  application {
    dependsOn 'bootstrap'
    dependsOn 'font-awesome'
    resource url: 'css/metisMenu.min.css'
    resource url: 'js/metisMenu.min.js'
    resource url: 'css/sb-admin-2.css'
    resource url: 'js/sb-admin-2.js'
    resource url: 'css/bootstrap-editable.css'
    resource url:'js/bootbox.min.js'
    resource url:'js/gokb.js'
    resource url:'js/inline-content.js'
    resource url:'js/summernote.min.js'
    resource url:'css/summernote.css'
    resource url:'css/summernote-bs3.css'
    resource url:'js/action-forms.js'
    resource url:'js/annotations.js'
    resource url:'css/annotations.css'
    resource url:'js/bootstrap-editable.js'
    resource url:'css/security-styles.css'
    resource url:'js/select-all-multistate.js'
    resource url:'elasticsearch/es_mapping.sh'
    resource url:'elasticsearch/es_settings.sh'
  }
  editable {
    resource url:'css/select2.css'
    resource url:'css/select2-bootstrap3.css'
    resource url:'js/editable.js.gsp'
    resource url:'js/moment.min.js'
    resource url:'js/select2.min.js'
  }
  dynatree {
    resource url:'js/jquery.dynatree.min.js'
    resource url:'css/ui.dynatree.css'
  }
}
