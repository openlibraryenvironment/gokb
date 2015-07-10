import org.gokb.cred.*
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils

class UserDetailsFilters {

  def springSecurityService
  def aclUtilService
  def gokbAclService

  // grailsApplication.config.appDefaultPrefs

  def filters = {
    
    // DO not allow any pages to be served from browser cache.
    noCacheFilter(controller:'*', action:'*') {
       after = {
           response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate") // HTTP 1.1.
           response.setHeader("Pragma", "no-cache") // HTTP 1.0.
           response.setDateHeader("Expires", -1)
       }
    }
    
    setUserFilter(controller:'*', action:'*') {
      before = {
        // if ( session.sessionPreferences == null ) {
        //   session.sessionPreferences = grailsApplication.config.appDefaultPrefs
        // }
        // else {
        // }
        if ( springSecurityService.principal instanceof String ) {
          //log.debug("User is string: ${springSecurityService.principal}");
        }
        else if (springSecurityService.principal?.id != null ) {
          request.user = User.get(springSecurityService.principal.id)
          request.userOptions = request.user.getUserOptions()
          
          if (!session.menus) {
          
            // Map to hold the menus.
            final Map <String, LinkedHashMap<String, Map>> menus = new HashMap <String, LinkedHashMap<String, Map>>().withDefault {
              new LinkedHashMap<String, Map>().withDefault {
                new ArrayList()
              }
            }
            
            // Add to the session.
            session.menus = menus
            
            // Step 1 : List all domains available to this user order by type, grouped into type
            def domains = KBDomainInfo.createCriteria().list {
              
              ilike ('dcName', 'org.gokb%')
              
              createAlias ("type", "menueType")
              
              order ('menueType.sortKey','asc')
              order ('menueType.value','asc')
              order ('dcSortOrder','asc')
              order ('displayName','asc')
            }
            
            domains.each { d ->
      
              // Get the target class.
              Class tc = Class.forName(d.dcName)
              
              if ( tc.isTypeReadable() ) {
      
                // Find any searches for that domain that the user has access to and add them to the menu section
                def searches_for_this_domain = grailsApplication.config.globalSearchTemplates.findAll{it.value.baseclass==d.dcName}
                
                searches_for_this_domain.each { key, val ->
                  
                  // Add a menu item.
                  menus["search"]["${d.type.value}"] << [
                    text : val.title,
                    link : ['controller' : 'search', 'action' : 'index', 'params' : [qbe:'g:'+ key]],
                    attr : ['title' : "Search ${val.title}"]
                  ]
                }
              }
      
              // Add if creatable.
              if ( tc.isTypeCreatable() ) { 
                if ( d.dcName == 'org.gokb.cred.TitleInstancePackagePlatform' ) {
                  // Suppress for now.
                  log.error ("TitleInstancePackagePlatform.isTypeCreatable() is testing true!!")
                }
                menus["create"]["${d.type.value}"] << [
                  text : d.displayName,
                  link : ['controller' : 'create', 'action' : 'index', 'params' : [tmpl:d.dcName]],
                  attr : ['title' : "New ${d.displayName}"]
                ]
              }
            }
          }

          // Get user curatorial groups
          if ( ! session.curatorialGroups ) {
            session.curatorialGroups = [];
            request.user.curatoryGroups.each { cg ->
              session.curatorialGroups.add([id:cg.id, name:cg.name]);
            }
          }

          if ( session.userPereferences == null ) {
            //log.debug("Set up user prefs");
            session.userPereferences = request.user.getUserPreferences()
          }
        }
      }
    }
  }
}
