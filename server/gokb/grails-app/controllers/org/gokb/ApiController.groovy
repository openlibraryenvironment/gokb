package org.gokb

import grails.plugins.springsecurity.Secured
import grails.converters.JSON

import org.codehaus.groovy.grails.web.json.JSONObject
import org.gokb.refine.RefineOperation
import org.gokb.refine.RefineProject


class ApiController {

	// Internal API return object that ensures consistent formatting of API return objects
	private def apiReturn = {result, String message = "", String status = "success" ->
		def data = [
					code		: (status),
					result		: (result),
					message		: (message),
				]

		def json = data as JSON
		log.debug (json)
		render (text: "${params.callback}(${json})", contentType: "application/javascript", encoding: "UTF-8")
	}

	private def getFileRepo() {

		String fs = "./project-files/"
		File f = new File(fs)
		if ( ! f.exists() ) {
			log.debug("Creating upload directory path")
			f.mkdirs()
		}

		// Return the filestore
		fs
	}

	def index() {
	}

	//  @Secured(["ROLE_USER"])
	def describe() {
		apiReturn(RefineOperation.findAll ())
	}

	def saveOperations() {
		// Get the operations as a list.

		// The line below looks like it replaces like with like but because the
		// second parameter is a regex it gets double escaped.
		def ops = params.operations.replaceAll("\\\\\"", "\\\\\"")
		ops = JSON.parse(params.operations)

		// Save each operation to the database
		ops.each {
			try {
				new RefineOperation(
						description : it['operation']['description'],
						operation : new LinkedHashMap(it['operation'])
						).save(failOnError : true)
			} catch (Exception e) {
				log.error(e)
			}
		}

		apiReturn( null, "Succesfully saved the operations.")
	}


	@Secured(["ROLE_USER"])
	def ingest() {

		def result = apiReturn (
				[
					[ name:'rule1', blurb:'blurb' ],
					[ name:'rule2', blurb:'blurb' ],
					[ name:'rule3', blurb:'blurb' ],
					[ name:'rule4', blurb:'blurb' ],
				]
				)

		render result as JSON
	}

	def projectList() {
		apiReturn ( RefineProject.findAll (params) )
	}

	def projectCheckout() {

		if (params.projectID) {
			// Get the project file.
			def project = RefineProject.load(params.long("projectID"))
			def file = new File(getFileRepo() + project.file)
			response.setContentType("application/octet-stream")
			response.setHeader("Content-disposition", "attachment;filename=${file.getName()}")
			response.outputStream << file.newInputStream()

		} else response.status = 404;
	}
}
