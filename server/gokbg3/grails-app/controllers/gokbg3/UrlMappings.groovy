package gokbg3

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

class UrlMappings {

    def springSecurityService

    static mappings = {
        // "/$controller/$action?/$id?(.$format)?"{
        "/oai/$id?" (controller: 'oai', action: 'index')
        "/resource/show/$type/$id" (controller: 'resource', action:'show')
        "/package" (controller: 'packages')

        group "/rest", {
            get "/packages/$id/$action" (controller: 'package', namespace:'rest')
            get "/packages/$id" (controller: 'package', namespace: 'rest', action:'show')
            put "/packages/$id" (controller: 'package', namespace: 'rest', action:'update')
            delete "/packages/$id" (controller: 'package', namespace: 'rest', action:'delete')
            get "/packages" (controller: 'package', namespace:'rest', action:'index')

            get "/refdata/categories/$id" (controller: 'refdata', namespace: 'rest', action: 'showCategory')
            get "/refdata/values/$id" (controller: 'refdata', namespace: 'rest', action:'showValue')
            get "/refdata" (controller: 'refdata', namespace:'rest', action: 'index')

            get "/profile" (controller: 'profile', namespace:'rest', action: 'show')
            put "/profile" (controller: 'profile', namespace:'rest', action: 'update')
            delete "/profile" (controller: 'profile', namespace:'rest', action: 'delete')
        }
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
        "403"(controller:'error', action:'forbidden', params: params)
        "401"(controller:'error', action:'unauthorized')
        "400"(controller:'error', action:'badRequest')
    }
}
