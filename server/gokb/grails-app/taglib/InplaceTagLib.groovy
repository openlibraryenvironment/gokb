import com.k_int.kbplus.*

class InplaceTagLib {

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
    def oid = attrs.owner.id != null ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
    def id = attrs.id ?: "${oid}:${attrs.field}"
   
    out << "<span>"
   
    // Output an editable link
    out << "<span id=\"${id}\" class=\"xEditableManyToOne\" data-pk=\"${oid}\" data-type=\"select\" data-name=\"${attrs.field}\" data-source=\"${data_link}\" data-url=\"${update_link}\">"

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
    def oid = attrs.owner.id != null ? "${attrs.owner.class.name}:${attrs.owner.id}" : ''
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
    out << "<input type=\"hidden\" name=\"${attrs.name}\" data-domain=\"${attrs.baseClass}\" "
    if ( attrs.id ) {
      out << "id=\"${attrs.id}\" "
    }
    if ( attrs.style ) {
      out << "style=\"${attrs.style}\" "
    }
    out << "class=\"simpleReferenceTypedown ${attrs.class}\" />"
  }


  def simpleHiddenRefdata = { attrs, body ->
    def data_link = createLink(controller:'ajaxSupport', action: 'getRefdata', params:[id:attrs.refdataCategory,format:'json'])
    out << "<input type=\"hidden\" name=\"${attrs.name}\"/>"
    out << "<a href=\"#\" class=\"simpleHiddenRefdata\" data-type=\"select\" data-source=\"${data_link}\" data-hidden-id=\"${attrs.name}\">"
    out << body()
    out << "</a>";
  }

  def addToCollection() {
    log.debug("AjaxController::addToCollection ${params}");

    def contextObj = resolveOID2(params.__context)
    def domain_class = grailsApplication.getArtefact('Domain',params.__newObjectClass)

    if ( domain_class ) {

      if ( contextObj ) {
        log.debug("Create a new instance of ${params.__newObjectClass}");

        def new_obj = domain_class.getClazz().newInstance();

        domain_class.getPersistentProperties().each { p -> // list of GrailsDomainClassProperty
          log.debug("${p.name} (assoc=${p.isAssociation()}) (oneToMany=${p.isOneToMany()}) (ManyToOne=${p.isManyToOne()}) (OneToOne=${p.isOneToOne()})");
          if ( params[p.name] ) {
            if ( p.isAssociation() ) {
              if ( p.isManyToOne() || p.isOneToOne() ) {
                // Set ref property
                log.debug("set assoc ${p.name} to lookup of OID ${params[p.name]}");
                // if ( key == __new__ then we need to create a new instance )
                new_obj[p.name] = resolveOID2(params[p.name])
              }
              else {
                // Add to collection
                log.debug("add to collection ${p.name} for OID ${params[p.name]}");
                new_obj[p.name].add(resolveOID2(params[p.name]))
              }
            }
            else {
              log.debug("Set simple prop ${p.name} = ${params[p.name]}");
              new_obj[p.name] = params[p.name]
            }
          }
        }

        if ( params.__recip ) {
          log.debug("Set reciprocal property ${params.__recip} to ${contextObj}");
          new_obj[params.__recip] = contextObj
        }

        log.debug("Saving ${new_obj}");

        if ( new_obj.save() ) {
          log.debug("Saved OK");
        }
        else {
          new_obj.errors.each { e ->
            log.debug("Problem ${e}");
          }
        }
      }
      else {
        log.debug("Unable to locate instance of context class with oid ${params.__context}");
      }
    }
    else {
      log.error("Unable to ookup domain class ${params.__newObjectClass}");
    }

    redirect(url: request.getHeader('referer'))
  }

}
