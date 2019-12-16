package gokbg3

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

class UrlMappings {

    def springSecurityService

    static mappings = {
        // "/$controller/$action?/$id?(.$format)?"{
        "/oai/$id?" (controller: 'oai', action: 'index')
        "/resource/show/$type/$id" (controller: 'resource', action:'show')
        "/error" (view:'/error')
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller:'public',action:'index')
        "500"(controller:'error', action:'forbidden', exception: NotFoundException)
        "500"(controller:'error', action:'unauthorized', exception: AccessDeniedException)
        "500"(controller:'error', action:'serverError')
        "405"(controller:'error', action:'wrongMethod')
        "404"(controller:'error', action:'notFound')
        "403"(controller:'error', action:'forbidden')
        "401"(controller:'error', action:'unauthorized')
        "400"(controller:'error', action:'badRequest')
    }
}
