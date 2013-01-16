class UrlMappings {

	static mappings = {
		"/$controller/$action?/$id?"{
			constraints {
				// apply constraints here
			}
		}

		"/"(controller:'home',action:'index')
		"/rules"(controller:'home',action:'showRules')
		"500"(view:'/error')
	}
}
