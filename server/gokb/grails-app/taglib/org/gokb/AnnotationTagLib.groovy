package org.gokb

import org.codehaus.groovy.grails.io.support.GrailsResourceUtils
import org.codehaus.groovy.grails.web.pages.GroovyPage
import org.gokb.cred.Role
import org.gokb.cred.User
import org.hibernate.proxy.HibernateProxy

class AnnotationTagLib {
  static defaultEncodeAs = 'raw'
  
  def springSecurityService

  private String getGspFilePath(GroovyPage page) {
    String name = page.getGroovyPageFileName()
    int index = name.lastIndexOf(GrailsResourceUtils.VIEWS_DIR_PATH)

    if (index > -1) {
      name = name.substring(index + GrailsResourceUtils.VIEWS_DIR_PATH.length())
    }

    // Return the name
    name
  }

  def annotatedLabel = { attributes, body ->

    // Set default attributes.
    def attr = [
      "element" : "span",
    ]

    // Override with supplied attributes.
    attr.putAll(attributes)

    // Get the element.
    def element = attr.remove("element")

    // Get the annotation object.
    def owner = attr.remove('owner')
    def property = attr.remove('property')
    Annotation annotation
    if ( owner && property) {

      // Get the GSP that called this tag.
      String view = getGspFilePath(pageScope.getOwner())

      // Get the label for the object property for this view.
      annotation = Annotation.getFor(owner, property, view)
    }
    
    // Annotation required?
    User user = springSecurityService.currentUser
    boolean isAdmin = user.getAuthorities().find { Role role -> 
      "ROLE_ADMIN".equalsIgnoreCase(role.authority)
    }
    
    isAdmin = false
    
    // Should the annotation be shown?
    boolean show_annotation = session.userPereferences.showInfoIcon && (isAdmin || annotation?.value != null)
    
    // Add the necessary class if we need it.
    if ( show_annotation ) {
      attr['class'] = attr['class'] ? "${attr['class']} annotated" : "annotated"
    }

    // Now just output the desired element.
    out << "<${element}"
    attr.each { att, val ->
      out << " ${att}=\"${val}\" "
    }
    out << ">" + body() + "</${element}>"

    // Output the annotation if we should.
    if (show_annotation) {
      
      // Map of lists of values.
      def ann_props = [:].withDefault {
        []
      }
      
      // Add our props.
      ann_props['data-url'] << createLink(controller:'ajaxSupport', action: 'editableSetValue')
      ann_props['data-pk'] << "${deproxy(annotation).class.name}:${annotation.id}"
      ann_props['data-name'] << "value"
      ann_props['class'] << 'annotation'
      
      if (isAdmin) {
        // Add a title to direct admins to double click.
        ann_props['title'] << "Double-click to edit this annotation."
        ann_props['class'] << 'annotation-editable'
      }
      
      if (attr['id']) {
        // Add an extra class.
        ann_props['class'] << 'annotation-editable'
      }

      // Now output the annotation in an adjacent div tag.
      out << "<div"
      ann_props.each {p_name, List p_value ->
        p_value.join(" ")
        out << " ${p_name}=\"${p_value.join(' ')}\""
      }
      out << ">${annotation.value ?: 'not set'}</div>"
    }
  }
  
  public static <T> T deproxy(def element) {
    if (element instanceof HibernateProxy) {
      return (T) ((HibernateProxy) element).getHibernateLazyInitializer().getImplementation();
    }
    return (T) element;
  }
}
