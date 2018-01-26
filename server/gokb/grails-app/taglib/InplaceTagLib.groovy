import groovy.json.JsonBuilder
import java.net.Authenticator.RequestorType;

import com.k_int.kbplus.*
import org.gokb.cred.*;
import com.k_int.ClassUtils

import org.hibernate.proxy.HibernateProxy

class InplaceTagLib {

  def genericOIDService
  def springSecurityService
  
  private boolean checkEditable (attrs, body, out) {
    
    // See if there is an owner attribute on the request - owner will be the domain object asking to be edited.
    def owner = attrs.owner ? ClassUtils.deproxy(attrs.owner) : null
    
    boolean cur = request.curator != null ? request.curator.size() > 0 : true
    
    // Default editable value.
    boolean tl_editable = cur  || (params.curationOverride == "true")
    
    if (tl_editable && owner?.respondsTo("isEditable")) {
      tl_editable = owner.isEditable()
    }
    
    // If not editable then we should output as value only and return the value.
    if (!tl_editable) {
      def content = (owner?."${attrs.field}" ? renderObjectValue (owner."${attrs.field}") : body()?.trim() )
      out << "<span class='readonly${content ? '' : ' editable-empty'}' title='This ${owner?.respondsTo('getNiceName') ? owner.getNiceName() : 'component' } is read only.' >${content ?: 'Empty'}</span>"
    }

    tl_editable
  }

  /**
   * Attributes:
   *   owner - Object
   *   field - property
   *   id [optional] - 
   *   class [optional] - additional classes
   */
  def xEditable = { attrs, body ->
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;
    
    def owner = ClassUtils.deproxy(attrs.owner);

    def oid = owner.id != null ? "${owner.class.name}:${owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    
    // Default the format.
    attrs."data-format" = attrs."data-format" ?: 'yyyy/MM/dd' 

    out << "<span id=\"${id}\" class=\"xEditableValue ${attrs.class?:''} ${attrs.type == 'date' ? 'date' : ''}\""
    
    if ( oid && ( oid != '' ) ) out << " data-pk=\"${oid}\""
    out << " data-name=\"${attrs.field}\""
    
    // SO: fix for FF not honouring no-wrap css.
    if ((attrs.type ?: 'textarea') == 'textarea') {
      out << " data-tpl=\"${'<textarea wrap=\'off\'></textarea>'.encodeAsHTML()}\""
    } 

    def data_link = null
    switch ( attrs.type ) {
      case 'date':
        data_link = createLink(controller:'ajaxSupport', action: 'editableSetValue', params:[type:'date', format: (attrs."data-format"?:'yyyy-MM-dd')])
//        out << " data-type='dateui' data-format='${attrs."data-format"?:'YYYY-MM-DD'}' data-datepicker='{minYear : 1900,smartDays:true}' data-viewformat='MM/DD/YYYY'"
//        if (!attrs."data-value") {
//          if (owner[attrs.field]) {
//    
//            // Date format.
//            def sdf = new java.text.SimpleDateFormat(attrs."format"?:'yyyy-MM-dd')
//            attrs."data-value" = sdf.format(owner[attrs.field])
//          } else {
//            attrs."data-value" = ""
//          }
//        }
//        
//        out << " data-value='${attrs.'data-value'}'"
        
        out << " data-type=\"text\" data-format='${attrs."data-format"?:'yyyy-MM-dd'}'"
        break;
      case 'string':
      default:
        data_link = createLink(controller:'ajaxSupport', action: 'editableSetValue')
        out << " data-type=\"${attrs.type?:'textarea'}\""
        break;
    }

    out << " data-url=\"${data_link}\""
    out << ">"

    if ( body ) {
      out << body()
    }
    else {
      if (attrs.type!='date' ) {
        out << owner[attrs.field]
      } else if (owner[attrs.field]){
        def sdf = new java.text.SimpleDateFormat(attrs."data-format"?:'yyyy-MM-dd')
        out << sdf.format(owner[attrs.field])
      }
    }
    out << "</span>"
  }

  def xEditableRefData = { attrs, body ->

    User user = springSecurityService.currentUser
    boolean isAdmin = user.getAuthorities().find { Role role ->
      "ROLE_ADMIN".equalsIgnoreCase(role.authority)
    }
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;

    def owner = ClassUtils.deproxy( attrs.remove("owner") )

    // out << "editable many to one: <div id=\"${attrs.id}\" class=\"xEditableManyToOne\" data-type=\"select2\" data-config=\"${attrs.config}\" />"
    def data_link = createLink(controller:'ajaxSupport', action: 'getRefdata', params:[id:attrs.remove("config"),format:'json'])
    def update_link = createLink(controller:'ajaxSupport', action: 'genericSetRel')
    def oid = owner.id != null ? "${owner.class.name}:${owner.id}" : ''
    def id = attrs.remove("id") ?: "${oid}:${attrs.field}"
    def type = attrs.remove("type") ?: "select"
    def field = attrs.remove("field")
    attrs['class'] = ["xEditableManyToOne"]

    out << "<span>"

    // Output an editable link
    out << "<span id=\"${id}\" "
    if ( ( owner != null ) && ( owner.id != null ) ) {
      out << "data-pk=\"${oid}\" "
    }

    out << "data-url=\"${update_link}\" "
    
    def attributes = attrs.collect({k, v -> 
      
      if (v instanceof Collection) {
        v = v.collect({ val -> 
          "${val}"
        }).join(" ")
      }
      "${k}=\"${v.encodeAsHTML()}\""
    }).join(" ")
    out << "data-type=\"${type}\" data-name=\"${field}\" data-source=\"${data_link}\" ${attributes} >"

    // Here we can register different ways of presenting object references. The most pressing need to be
    // outputting a span containing an icon for refdata fields.
    out << renderObjectValue(owner[field])

    out << "</span>"

    // If the caller specified an rdc attribute then they are describing a refdata category.
    // We want to add a link to the category edit page IF the annotation is editable.

    if ( isAdmin ) {
      RefdataCategory rdc = RefdataCategory.findByDesc(attrs.config)
      if ( rdc ) {
        out << '&nbsp;<a href="'+createLink(controller:'resource', action: 'show', id:'org.gokb.cred.RefdataCategory:'+rdc.id)+'">Refdata</a><br/>'
      }
    }

    out << "</span>"
  }

  /**
   * ToDo: This function is a duplicate of the one found in AjaxController, both should be moved to a shared static utility
   */
  def renderObjectValue(value) {
    def result=''
    if ( value ) {
      switch ( value.class ) {
        case org.gokb.cred.RefdataValue.class:
          if ( value.icon != null ) {
            result="<span class=\"select-icon ${value.icon}\"></span>${value.value}"
          }
          else {
            result=value.value
          }
          break;
        default:
          result=value.toString();
      }
    }
    result;
  }
  
  def xEditableManyToOne = { attrs, body ->
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;
    
    def owner = ClassUtils.deproxy(attrs.owner)
    
    // out << "editable many to one: <div id=\"${attrs.id}\" class=\"xEditableManyToOne\" data-type=\"select2\" data-config=\"${attrs.config}\" />"
    def data_link = createLink(controller:'ajaxSupport', action: 'getRefdata', params:[id:attrs.config,format:'json'])
    def oid = ( ( owner != null ) && ( owner.id != null ) ) ? "${owner.class.name}:${owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    out << "<a href=\"#\" id=\"${id}\" class=\"xEditableManyToOne\" data-pk=\"${oid}\" data-type=\"select\" data-name=\"${attrs.field}\" data-source=\"${data_link}\">"
    out << body()
    out << "</a>";
  }

  def relation = { attrs, body ->
    out << "<span class=\"${attrs.class}\" id=\"${attrs.domain}:${attrs.pk}:${attrs.field}:${attrs.id}\">"
    if ( body ) {
      out << body()
    }
    out << "</span>"
  }

  def relationAutocomplete = { attrs, body ->
  }
  
  def xEditableFieldNote = { attrs, body ->
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;
    
    def owner = ClassUtils.deproxy( attrs.owner )

    def data_link = createLink(controller:'ajaxSupport', action: 'setFieldTableNote')
    data_link = data_link +"/"+owner.id +"?type=License"
    def oid = owner.id != null ? "${owner.class.name}:${owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    def org = ""
    if (attrs.owner.getNote("${attrs.field}")){
      org = owner.getNote("${attrs.field}").owner.content
    }
    else{
      org = owner.getNote("${attrs.field}")
    }

    out << "<span id=\"${id}\" class=\"xEditableValue ${attrs.class?:''}\" data-type=\"textfield\" data-pk=\"${oid}\" data-name=\"${attrs.field}\" data-url=\"${data_link}\"  data-original-title=\"${org}\">"
    if ( body ) {
      out << body()
    }
    else {
      out << org
    }
    out << "</span>"
  }


  /**
   * simpleReferenceTypedown - create a hidden input control that has the value fully.qualified.class:primary_key and which is editable with the
   * user typing into the box. Takes advantage of refdataFind and refdataCreate methods on the domain class.
   */ 
  def simpleReferenceTypedown = { attrs, body ->
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;
    
    out << "<input type=\"hidden\" value=\"${attrs.value?:''}\" name=\"${attrs.name}\" data-domain=\"${attrs.baseClass}\" "
    if ( attrs.id ) {
      out << "id=\"${attrs.id}\" "
    }
    if ( attrs.style ) {
      out << "style=\"${attrs.style}\" "
    }

    if ( ( attrs.value != null ) && ( attrs.value.length() > 0 ) ) {
      def o = genericOIDService.resolveOID2(attrs.value)
      out << "data-displayValue=\"${o.toString()}\" ";
    }

    if ( attrs.elastic ) {
      out << "data-elastic=\"${attrs.elastic}\""
    }

    if ( attrs.filter1 ) {
      out << "data-filter1=\"${attrs.filter1}\" "
    }

    out << "class=\"simpleReferenceTypedown ${attrs.class}\" />"
  }

  def manyToOneReferenceTypedown = { attrs, body ->
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;
    
    def owner = ClassUtils.deproxy(attrs.owner)
    
    def oid = attrs.owner.id != null ? "${owner.class.name}:${owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    def update_link = createLink(controller:'ajaxSupport', action: 'genericSetRel')

    def follow_link = null;
    if ( owner != null && owner[attrs.field] != null && grailsApplication.config.globalDisplayTemplates[owner.class.name]) {
      follow_link = createLink(controller:'resource', action: 'show')
      follow_link = follow_link + '/' + owner[attrs.field].class.name + ':' + owner[attrs.field].id;
    }

    out << "<a href=\"#\" data-domain=\"${attrs.baseClass}\" id=\"${id}\" class=\"xEditableManyToOneS2\" "

    if ( ( attrs.filter1 != null ) && ( attrs.filter1.length() > 0 ) ) {
      out << "data-filter1=\"${attrs.filter1}\" "
    }

    if ( owner?.id != null )
      out << "data-pk=\"${oid}\" "

    out << "data-type=\"select2\" data-name=\"${attrs.field}\" data-url=\"${update_link}\" >"
    out << body()
    out << "</a>";
    
    if( follow_link ){
      out << ' &nbsp; <a href="'+follow_link+'">Follow Link</a>'
    }
  }

  def manyToOneReferenceTypedownOld = { attrs, body ->
    
    // The check editable should output the read only version so we should just exit
    // if read only.
    if (!checkEditable(attrs, body, out)) return;
    
    def owner = ClassUtils.deproxy(attrs.owner)
    
    def oid = attrs.owner.id != null ? "${owner.class.name}:${owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    def update_link = createLink(controller:'ajaxSupport', action: 'genericSetRel')
    out << "<span data-domain=\"org.gokb.cred.Org\" id=\"${id}\" class=\"xEditableManyToOneS2\" data-pk=\"${oid}\" data-type=\"select2\" data-name=\"${attrs.field}\" data-value=\"\" data-url=\"${update_link}\" >"
    out << body()
    out << "</span>";
  }


  def simpleHiddenRefdata = { attrs, body ->
    
    def data_link = createLink(controller:'ajaxSupport', action: 'getRefdata', params:[id:attrs.refdataCategory,format:'json'])
    out << "<input type=\"hidden\" name=\"${attrs.name}\"/>"
    out << "<a href=\"#\" class=\"simpleHiddenRefdata\" data-type=\"select\" data-source=\"${data_link}\" data-hidden-id=\"${attrs.name}\">"
    out << body()
    out << "</a>";
  }

  def componentLink = { Map attrs, body ->
    
    def obj = attrs.remove('object')
    if ( obj != null ) {
      def object = ClassUtils.deproxy(obj)
      def object_link = createLink(controller:'resource', action: 'show', id:"${object.class.name}:${object.id}")
      out << "<a href=\"${object_link}\""
      
      // Ensure we pipe out the rest of the parameters too.
      attrs.each { name, val ->
        out << " ${name}=\"${val}\""
      }
      out << " >"
      out << body()
      out << "</a>"
    }
  }

  def rdcLabel = { Map attrs, body ->
    def rdc = RefdataCategory.findByDesc(attrs.cat)
    if ( ( rdc ) && ( rdc.label ) ) {
      out << rdc.label
    }
    else {
      out << attrs.default
    }
  }
}
