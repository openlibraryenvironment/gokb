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
            post "/packages" (controller: 'package', namespace: 'rest', action:'save')
            get "/packages" (controller: 'package', namespace:'rest', action:'index')

            get "/refdata/categories/$id" (controller: 'refdata', namespace: 'rest', action: 'showCategory')
            get "/refdata/values/$id" (controller: 'refdata', namespace: 'rest', action:'showValue')
            get "/refdata" (controller: 'refdata', namespace:'rest', action: 'index')

            get "/orgs/$id/$action" (controller: 'org', namespace:'rest')
            get "/orgs/$id" (controller: 'org', namespace: 'rest', action:'show')
            put "/orgs/$id" (controller: 'org', namespace: 'rest', action:'update')
            delete "/orgs/$id" (controller: 'org', namespace: 'rest', action:'delete')
            get "/orgs" (controller: 'org', namespace:'rest', action:'index')

            get "/provider/$id/$action" (controller: 'org', namespace:'rest')
            get "/provider/$id" (controller: 'org', namespace: 'rest', action:'show')
            put "/provider/$id" (controller: 'org', namespace: 'rest', action:'update')
            delete "/provider/$id" (controller: 'org', namespace: 'rest', action:'delete')
            get "/provider" (controller: 'org', namespace:'rest', action:'index')
        
            get "/publisher/$id/$action" (controller: 'org', namespace:'rest')
            get "/publisher/$id" (controller: 'org', namespace: 'rest', action:'show')
            put "/publisher/$id" (controller: 'org', namespace: 'rest', action:'update')
            delete "/publisher/$id" (controller: 'org', namespace: 'rest', action:'delete')
            get "/publisher" (controller: 'org', namespace:'rest', action:'index')

            get "/platforms/$id/$action" (controller: 'platform', namespace:'rest')
            get "/platforms/$id" (controller: 'platform', namespace: 'rest', action:'show')
            put "/platforms/$id" (controller: 'platform', namespace: 'rest', action:'update')
            delete "/platforms/$id" (controller: 'platform', namespace: 'rest', action:'delete')
            get "/platforms" (controller: 'platform', namespace:'rest', action:'index')

            get "/identifiers/$id/$action" (controller: 'identifier', namespace:'rest')
            get "/identifiers/$id" (controller: 'identifier', namespace: 'rest', action:'show')
            post "/identifiers/$id" (controller: 'identifier', namespace: 'rest', action:'save')
            get "/identifiers" (controller: 'identifier', namespace:'rest', action:'index')

            get "/profile" (controller: 'profile', namespace:'rest', action: 'show')
            put "/profile" (controller: 'profile', namespace:'rest', action: 'update')
            delete "/profile" (controller: 'profile', namespace:'rest', action: 'delete')

            get "/entities" (controller: 'global', namespace:'rest', action: 'index')
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
