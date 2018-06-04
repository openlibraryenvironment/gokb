package gokbg3

class UrlMappings {

    def springSecurityService

    static mappings = {
        // "/$controller/$action?/$id?(.$format)?"{
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller:'public',action:'index')
        "500"(view:'/error')
        "404"(controller:'home', action:'index') {status = '404'}
        "403"(view:'/login/denied')
    }
}
