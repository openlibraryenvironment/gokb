package org.gokb

import org.codehaus.groovy.grails.io.support.GrailsResourceUtils
import org.codehaus.groovy.grails.web.pages.GroovyPage
import org.gokb.cred.Role
import org.gokb.cred.User

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
    Annotation annotation
    if ( attr['owner'] && attr['property']) {

      // Get the GSP that called this tag.
      String view = getGspFilePath(pageScope.getOwner())

      // Get the label for the object property for this view.
      annotation = Annotation.getFor(attr.remove('owner'), attr.remove('property'), view)
    }
    
    // Annotation required?
    User user = springSecurityService.currentUser
    boolean isAdmin = user.getAuthorities().find { Role role -> 
      "ROLE_ADMIN".equalsIgnoreCase(role.authority)
    }
    
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

      // Now output the label in an adjacent span div tag.
      out << "<div class=\"annotation${isAdmin ? ' annotation-editable ' : ''}${attr['id'] ? attr['id'] + '-annotation' : ''} \">${annotation.value ?: 'not set'}</div>"
    }
  }
}
