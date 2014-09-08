class UrlMappings {

  static mappings = {
    "/$controller/$action?/$id?"{
      constraints {
        // apply constraints here
      }
    }

    "/oai/$id"(controller:'oai',action:'index')

    "/"(controller:'welcome',action:'index')
    "/rules"(controller:'home',action:'showRules')
    "500"(view:'/error')
  }
}
