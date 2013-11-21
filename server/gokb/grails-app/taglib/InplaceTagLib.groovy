import com.k_int.kbplus.*
import org.hibernate.proxy.HibernateProxy

class InplaceTagLib {

  def genericOIDService

  /**
   * Attributes:
   *   owner - Object
   *   field - property
   *   id [optional] - 
   *   class [optional] - additional classes
   */
  def xEditable = { attrs, body ->

    def oid = attrs.owner.id != null ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"

    out << "<span id=\"${id}\" class=\"xEditableValue ${attrs.class?:''}\""
    out << " data-type=\"${attrs.type?:'textarea'}\""
    if ( oid && ( oid != '' ) ) 
      out << " data-pk=\"${oid}\""
    out << " data-name=\"${attrs.field}\""

    def data_link = null
    switch ( attrs.type ) {
      case 'date':
        data_link = createLink(controller:'ajaxSupport', action: 'editableSetValue', params:[type:'date',format:'yyyy/MM/dd'])
        break;
      case 'string':
      default:
        data_link = createLink(controller:'ajaxSupport', action: 'editableSetValue')
        break;
    }

    out << " data-url=\"${data_link}\""
    out << ">"

    if ( body ) {
      out << body()
    }
    else {
      if ( attrs.owner[attrs.field] && attrs.type=='date' ) {
        def sdf = new java.text.SimpleDateFormat(attrs.format?:'yyyy-MM-dd')
        out << sdf.format(attrs.owner[attrs.field])
      }
      else {
        out << attrs.owner[attrs.field]
      }
    }
    out << "</span>"
  }

  def xEditableRefData = { attrs, body ->
    // out << "editable many to one: <div id=\"${attrs.id}\" class=\"xEditableManyToOne\" data-type=\"select2\" data-config=\"${attrs.config}\" />"
    def data_link = createLink(controller:'ajaxSupport', action: 'getRefdata', params:[id:attrs.config,format:'json'])
    def update_link = createLink(controller:'ajaxSupport', action: 'genericSetRel')
    def oid = attrs.owner.id != null ? "${deproxy(attrs.owner.class).name}:${attrs.owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    def type = attrs.type ?: "select"
   
    out << "<span>"
   
    // Output an editable link
    out << "<span id=\"${id}\" class=\"xEditableManyToOne\" "
    if ( ( attrs.owner != null ) && ( attrs.owner.id != null ) ) {
      out << "data-pk=\"${oid}\" "
    }
    else {
    }
    out << "data-url=\"${update_link}\" "

    out << "data-type=\"${type}\" data-name=\"${attrs.field}\" data-source=\"${data_link}\" >"

    // Here we can register different ways of presenting object references. The most pressing need to be
    // outputting a span containing an icon for refdata fields.
    out << renderObjectValue(attrs.owner[attrs.field])

    out << "</span></span>"
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
    // out << "editable many to one: <div id=\"${attrs.id}\" class=\"xEditableManyToOne\" data-type=\"select2\" data-config=\"${attrs.config}\" />"
    def data_link = createLink(controller:'ajaxSupport', action: 'getRefdata', params:[id:attrs.config,format:'json'])
    def oid = ( ( attrs.owner != null ) && ( attrs.owner.id != null ) ) ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
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
   
    def data_link = createLink(controller:'ajaxSupport', action: 'setFieldTableNote')
    data_link = data_link +"/"+attrs.owner.id +"?type=License"
    def oid = attrs.owner.id != null ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    def org = ""
    if (attrs.owner.getNote("${attrs.field}")){
       org =attrs.owner.getNote("${attrs.field}").owner.content
    }
    else{
       org = attrs.owner.getNote("${attrs.field}")
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


    if ( attrs.filter1 ) {
      out << "data-filter1=\"${attrs.filter1}\" "
    }

    out << "class=\"simpleReferenceTypedown ${attrs.class}\" />"
  }

  def manyToOneReferenceTypedown = { attrs, body ->
    def oid = attrs.owner.id != null ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
    def update_link = createLink(controller:'ajaxSupport', action: 'genericSetRel')
    out << "<a href=\"#\" data-domain=\"${attrs.baseClass}\" id=\"${id}\" class=\"xEditableManyToOneS2\" "

    if ( ( attrs.filter1 != null ) && ( attrs.filter1.length() > 0 ) ) {
      out << "data-filter1=\"${attrs.filter1}\" "
    }

    if ( attrs.owner?.id != null )
      out << "data-pk=\"${oid}\" "

    out << "data-type=\"select2\" data-name=\"${attrs.field}\" data-url=\"${update_link}\" >"
    out << body()
    out << "</a>";
  }

  def manyToOneReferenceTypedownOld = { attrs, body ->
    def oid = attrs.owner.id != null ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
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

  def componentLink = { attrs, body ->
    if ( attrs.object != null ) {
      def object_link = createLink(controller:'resource', action: 'show', id:"${attrs.object.class.name}:${attrs.object.id}")
      out << "<a href=\"${object_link}\">"
      out << body()
      out << "</a>"
    }
  }

  public static <T> T deproxy(def element) {
    if (element instanceof HibernateProxy) {
      return (T) ((HibernateProxy) element).getHibernateLazyInitializer().getImplementation();
    }
    return (T) element;
  }

}
