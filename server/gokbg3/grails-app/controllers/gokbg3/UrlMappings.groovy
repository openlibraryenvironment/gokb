package gokbg3

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

class UrlMappings {

    def springSecurityService

    static mappings = {
        // "/$controller/$action?/$id?(.$format)?"{
        "/oai/$id?" (controller: 'oai', action: 'index')
        "/resource/show/$type/$id" (controller: 'resource', action:'show')
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller:'public',action:'index')
        "500"(view:'/error')
        "500"(view:'/login/denied', exception: NotFoundException)
        "500"(view:'/login/denied', exception: AccessDeniedException)
        "404"(controller:'home', action:'index') {status = '404'}
        "403"(view:'/login/denied')
        "401"(view:'/login/denied')
    }
}
