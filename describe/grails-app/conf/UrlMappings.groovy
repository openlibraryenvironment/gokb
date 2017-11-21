class UrlMappings {

	static mappings = {

		"/$controller/$action?"{
                }

		"/$controller/$id?/$action?"{
			constraints {
				// apply constraints here
			}
		}

		"/"(view:"/index")
		"500"(view:'/error')
	}
}
