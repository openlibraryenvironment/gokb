class UrlMappings {

  static mappings = {
    "/$controller/$action?/$id?"{
      constraints {
        // apply constraints here
      }
    }

    "/oai/$id"(controller:'oai',action:'index')

    "/"(controller:'home',action:'index')
    "/rules"(controller:'home',action:'showRules')
    "/nourl"(view:'/NoUrl')
    "500"(view:'/error')
  }
}
