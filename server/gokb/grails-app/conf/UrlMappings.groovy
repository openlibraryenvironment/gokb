class UrlMappings {

	static mappings = {
		"/$controller/$action?/$id?"{
			constraints {
				// apply constraints here
			}
		}

                "/resource/$oid?/$subpage?"(controller:'home',action:'index')
              
		"/"(controller:'home',action:'index')
		"500"(view:'/error')
	}
}
