package gokbg3

class UrlMappings {

    static mappings = {
        // "/$controller/$action?/$id?(.$format)?"{
        "/$controller/$action?/$id?"{
            constraints {
                // apply constraints here
            }
        }

        "/"(controller:'public',action:'index')
        "500"(view:'/error')
        "404"(view:'/notFound')
    }
}
