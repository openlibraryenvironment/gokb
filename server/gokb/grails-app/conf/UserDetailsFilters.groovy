import org.gokb.cred.*
import org.springframework.security.core.context.SecurityContextHolder as SCH
import grails.plugin.springsecurity.SpringSecurityUtils

class UserDetailsFilters {

  def springSecurityService
  def aclUtilService
  def gokbAclService
  def cacheHeadersService

  // grailsApplication.config.appDefaultPrefs

  def filters = {
    
    // DO not allow any pages to be served from browser cache.
    noCacheFilter(controller:'*', action:'*') {
       before = {
         
         // Use the caching service to set no caches before the action is executed.
         // This should allow us to override within the action.
         cacheHeadersService.cache (response, false)
       }
    }
    
    setUserFilter(controller:'*', action:'*') {
      before = {

        def user = springSecurityService.getCurrentUser()

        if (user) {
          request.user = user
          request.userOptions = user.getUserOptions()
          
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
              
              if ( tc ) {
              
                if ( tc.isTypeReadable() ) {
        
                  // Find any searches for that domain that the user has access to and add them to the menu section
                  def searches_for_this_domain = grailsApplication.config.globalSearchTemplates.findAll{it.value.baseclass==d.dcName}
                  
                  searches_for_this_domain.each { key, val ->
                    
                    // Add a menu item.
                    menus["search"]["${d.type.value}"] << [
                      text : val.title,
                      link : ['controller' : 'search', 'action' : 'index', 'params' : [qbe:'g:'+ key, init: 'true']],
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
