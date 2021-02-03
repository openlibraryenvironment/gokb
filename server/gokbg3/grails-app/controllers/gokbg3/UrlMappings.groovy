package gokbg3

import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

class UrlMappings {

  def springSecurityService

  static mappings = {
    // "/$controller/$action?/$id?(.$format)?"{
    "/oai/$id?"(controller: 'oai', action: 'index')
    "/resource/show/$type/$id"(controller: 'resource', action: 'show')
    "/package"(controller: 'packages')

    group "/rest", {
      post "/packages/$id/retire"(controller: 'package', namespace: 'rest', action:'retire')
      post "/packages/$id/tipps"(controller: 'package', namespace: 'rest', action: 'addTipps')
      put "/packages/$id/tipps"(controller: 'package', namespace: 'rest', action: 'updateTipps')
      patch "/packages/$id/tipps"(controller: 'package', namespace: 'rest', action: 'updateTipps')
      "/packages/$id/$action"(controller: 'package', namespace: 'rest')
      get "/packages/$id"(controller: 'package', namespace: 'rest', action: 'show')
      put "/packages/$id"(controller: 'package', namespace: 'rest', action: 'update')
      patch "/packages/$id"(controller: 'package', namespace: 'rest', action: 'update')
      delete "/packages/$id"(controller: 'package', namespace: 'rest', action: 'delete')
      post "/packages"(controller: 'package', namespace: 'rest', action: 'save')
      get "/packages"(controller: 'package', namespace: 'rest', action: 'index')

      get "/refdata/categories/$id"(controller: 'refdata', namespace: 'rest', action: 'showCategory')
      get "/refdata/values/$id"(controller: 'refdata', namespace: 'rest', action: 'showValue')
      get "/refdata"(controller: 'refdata', namespace: 'rest', action: 'index')

      post "/organizations/$id/retire"(controller: 'org', namespace: 'rest', action:'retire')
      get "/organizations/$id/$action"(controller: 'org', namespace: 'rest')
      post "/organizations"(controller: 'org', namespace: 'rest', action: 'save')
      get "/organizations/$id"(controller: 'org', namespace: 'rest', action: 'show')
      put "/organizations/$id"(controller: 'org', namespace: 'rest', action: 'update')
      patch "/organizations/$id"(controller: 'org', namespace: 'rest', action: 'update')
      delete "/organizations/$id"(controller: 'org', namespace: 'rest', action: 'delete')
      get "/organizations"(controller: 'org', namespace: 'rest', action: 'index')

      post "/orgs/$id/retire"(controller: 'org', namespace: 'rest', action:'retire')
      get "/orgs/$id/$action"(controller: 'org', namespace: 'rest')
      post "/orgs"(controller: 'org', namespace: 'rest', action: 'save')
      get "/orgs/$id"(controller: 'org', namespace: 'rest', action: 'show')
      put "/orgs/$id"(controller: 'org', namespace: 'rest', action: 'update')
      patch "/orgs/$id"(controller: 'org', namespace: 'rest', action: 'update')
      delete "/orgs/$id"(controller: 'org', namespace: 'rest', action: 'delete')
      get "/orgs"(controller: 'org', namespace: 'rest', action: 'index')

      post "/provider/$id/retire"(controller: 'org', namespace: 'rest', action:'retire')
      get "/provider/$id/$action"(controller: 'org', namespace: 'rest')
      post "provider"(controller: 'org', namespace: 'rest', action: 'save')
      get "/provider/$id"(controller: 'org', namespace: 'rest', action: 'show')
      put "/provider/$id"(controller: 'org', namespace: 'rest', action: 'update')
      patch "/provider/$id"(controller: 'org', namespace: 'rest', action: 'update')
      delete "/provider/$id"(controller: 'org', namespace: 'rest', action: 'delete')
      get "/provider"(controller: 'org', namespace: 'rest', action: 'index')

      post "/publisher/$id/retire"(controller: 'org', namespace: 'rest', action:'retire')
      get "/publisher/$id/$action"(controller: 'org', namespace: 'rest')
      post "/publisher"(controller: 'org', namespace: 'rest', action: 'save')
      get "/publisher/$id"(controller: 'org', namespace: 'rest', action: 'show')
      put "/publisher/$id"(controller: 'org', namespace: 'rest', action: 'update')
      patch "/publisher/$id"(controller: 'org', namespace: 'rest', action: 'update')
      delete "/publisher/$id"(controller: 'org', namespace: 'rest', action: 'delete')
      get "/publisher"(controller: 'org', namespace: 'rest', action: 'index')

      post "/platforms/$id/retire"(controller: 'platform', namespace: 'rest', action:'retire')
      get "/platforms/$id/$action"(controller: 'platform', namespace: 'rest')
      post "/platforms"(controller: 'platform', namespace: 'rest', action: 'save')
      get "/platforms/$id"(controller: 'platform', namespace: 'rest', action: 'show')
      put "/platforms/$id"(controller: 'platform', namespace: 'rest', action: 'update')
      patch "/platforms/$id"(controller: 'platform', namespace: 'rest', action: 'update')
      delete "/platforms/$id"(controller: 'platform', namespace: 'rest', action: 'delete')
      get "/platforms"(controller: 'platform', namespace: 'rest', action: 'index')

      get "/identifiers/$id/$action"(controller: 'identifier', namespace: 'rest')
      get "/identifiers/$id"(controller: 'identifier', namespace: 'rest', action: 'show')
      post "/identifiers"(controller: 'identifier', namespace: 'rest', action: 'save')
      get "/identifiers"(controller: 'identifier', namespace: 'rest', action: 'index')
      get "/identifier-namespaces"(controller: 'identifier', namespace: 'rest', action: 'namespace')

      get "/profile"(controller: 'profile', namespace: 'rest', action: 'show')
      get "/profile/jobs/"(controller: 'profile', namespace: 'rest', action: 'getJobs')
      patch "/profile/jobs/$id/cancel"(controller: 'profile', namespace: 'rest', action: 'cancelJob')
      delete "/profile/jobs/$id"(controller: 'profile', namespace: 'rest', action: 'deleteJob')
      put "/profile"(controller: 'profile', namespace: 'rest', action: 'update')
      patch "/profile"(controller: 'profile', namespace: 'rest', action: 'patch')
      delete "/profile"(controller: 'profile', namespace: 'rest', action: 'delete')

      get "/entities"(controller: 'global', namespace: 'rest', action: 'index')

      get "/users"(controller: 'users', namespace: 'rest', action: 'index')
      post "/users"(controller: 'users', namespace: 'rest', action: 'save')
      get "/users/$id"(controller: 'users', namespace: 'rest', action: 'show')
      put "/users/$id"(controller: 'users', namespace: 'rest', action: 'update')
      patch "/users/$id"(controller: 'users', namespace: 'rest', action: 'update')
      delete "/users/$id"(controller: 'users', namespace: 'rest', action: 'delete')

      get "/sources"(controller: 'sources', namespace: 'rest', action: 'index')
      get "/sources/$id"(controller: 'sources', namespace: 'rest', action: 'show')
      put "/sources/$id"(controller: 'sources', namespace: 'rest', action: 'update')
      patch "/sources/$id"(controller: 'sources', namespace: 'rest', action: 'update')
      post "/sources"(controller: 'sources', namespace: 'rest', action: 'save')

      get "/reviews"(controller: 'reviews', namespace: 'rest', action: 'index')
      get "/reviews/$id"(controller: 'reviews', namespace: 'rest', action: 'show')
      post "/reviews/$id"(controller: 'reviews', namespace: 'rest', action: 'save')
      put "/reviews/$id"(controller: 'reviews', namespace: 'rest', action: 'update')
      patch "/reviews/$id"(controller: 'reviews', namespace: 'rest', action: 'update')

      get "/curatoryGroups/$id/reviews"(controller: 'curatoryGroups', namespace: 'rest', action: 'getReviews')
      get "/curatoryGroups/$id/jobs"(controller: 'curatoryGroups', namespace: 'rest', action: 'getJobs')
      get "/curatoryGroups/$id"(controller: 'curatoryGroups', namespace: 'rest', action: 'show')
      get "/curatoryGroups"(controller: 'curatoryGroups', namespace: 'rest', action: 'index')

      get "/roles"(controller: 'roles', namespace: 'rest', action: 'index')

      post "/titles/$id/retire"(controller: 'title', namespace: 'rest', action:'retire')
      get "/titles/$id/history"(controller: 'title', namespace: 'rest', action:'getHistory')
      delete "/titles/$tid/history/$id" (controller: 'title', namespace: 'rest', action:'deleteHistoryEvent')
      post "/titles/$id/history"(controller: 'title', namespace: 'rest', action:'addHistory')
      put "/titles/$id/history"(controller: 'title', namespace: 'rest', action:'updateHistory')
      patch "/titles/$id/history"(controller: 'title', namespace: 'rest', action:'updateHistory')
      get "/titles/$id/tipps"(controller: 'title', namespace: 'rest', action:'tipps')
      "/titles/$id/$action"(controller: 'title', namespace:'rest')
      get "/titles/$id"(controller: 'title', namespace: 'rest', action:'show')
      put "/titles/$id"(controller: 'title', namespace: 'rest', action:'update')
      delete "/titles/$id"(controller: 'title', namespace: 'rest', action:'delete')
      post "/titles"(controller: 'title', namespace: 'rest', action:'save')
      get "/titles"(controller: 'title', namespace:'rest', action:'index')

      get "title-types" (controller: 'title', namespace:'rest', action:'getTypes')

      post "/journals"(controller: 'title', namespace: 'rest', action:'save') { type = 'journal' }
      get "/journals"(controller: 'title', namespace: 'rest', action:'index') { type = 'journal' }
      post "/books"(controller: 'title', namespace: 'rest', action:'save') { type = 'book' }
      get "/books"(controller: 'title', namespace: 'rest', action:'index') { type = 'book' }
      post "/databases"(controller: 'title', namespace: 'rest', action:'save') { type = 'database' }
      get "/databases"(controller: 'title', namespace: 'rest', action:'index') { type = 'database' }

      post "/tipps/$id/retire"(controller: 'tipp', namespace: 'rest', action:'retire')
      get "/tipps/$id/coverage"(controller: 'tipp', namespace: 'rest', action: 'getCoverage')
      get "/tipps/$id"(controller: 'tipp', namespace: 'rest', action: 'show')
      put "/tipps/$id"(controller: 'tipp', namespace: 'rest', action: 'update')
      delete "/tipps/$id"(controller: 'tipp', namespace: 'rest', action: 'delete')
      post "/tipps"(controller: 'tipp', namespace: 'rest', action: 'save')
      get "/tipps"(controller: 'tipp', namespace: 'rest', action: 'index')

      get "/package-titles/$id/coverage"(controller: 'tipp', namespace: 'rest', action: 'getCoverage')
      get "/package-titles/$id"(controller: 'tipp', namespace: 'rest', action: 'show')
      put "/package-titles/$id"(controller: 'tipp', namespace: 'rest', action: 'update')
      delete "/package-titles/$id"(controller: 'tipp', namespace: 'rest', action: 'delete')
      post "/package-titles"(controller: 'tipp', namespace: 'rest', action: 'save')
      get "/package-titles"(controller: 'tipp', namespace: 'rest', action: 'index')

      get "/package-scopes" (controller: 'refdata', namespace: 'rest', action: 'packageScope')
      get "/coverage-depth" (controller: 'refdata', namespace: 'rest', action: 'coverageDepth')
      get "/review-types" (controller: 'refdata', namespace: 'rest', action: 'reviewType')

      get "/jobs" (controller: 'jobs', namespace: 'rest', action: 'index')
      get "/jobs/$id" (controller: 'jobs', namespace: 'rest', action: 'show')
      patch "/jobs/$id/cancel" (controller: 'jobs', namespace: 'rest', action: 'cancel')
      delete "/jobs/$id" (controller: 'jobs', namespace: 'rest', action: 'delete')
    }
    "/$controller/$action?/$id?" {
      constraints {
        // apply constraints here
      }
    }

    "/"(controller: 'public', action: 'index')
    "500"(controller: 'error', action: 'forbidden', exception: NotFoundException)
    "500"(controller: 'error', action: 'unauthorized', exception: AccessDeniedException)
    "500"(controller: 'error', action: 'serverError')
    "405"(controller: 'error', action: 'wrongMethod')
    "404"(controller: 'error', action: 'notFound')
    "403"(controller: 'error', action: 'forbidden', params: params)
    "401"(controller: 'error', action: 'unauthorized')
    "400"(controller: 'error', action: 'badRequest')
  }
}
