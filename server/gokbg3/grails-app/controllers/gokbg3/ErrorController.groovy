package gokbg3

import grails.converters.JSON
import groovy.util.logging.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

@Slf4j
class ErrorController {
  def springSecurityService

  def serverError() {
    def resp = [code: 500, message:'Server Error']
    withFormat {
      html {
        forward controller: 'home', action:'index', params:(params)
      }
      json {
        response.sendError(500)
        render resp as JSON
      }
    }
  }

  def notFound() {
    def resp = [code: 404, message:'Not Found']
    withFormat {
      html {
        forward controller: 'home', action:'index', params:(params), status: 404
      }
      json {
        response.sendError(404)
        render resp as JSON
      }
    }
  }
  
  def forbidden() {
    def resp = [code: 403, message:'Forbidden']
    withFormat {
      html {
        redirect (uri:'login/denied', params:[status:403])
      }
      json {
        response.sendError(401)
        render resp as JSON
      }
    }
  }

  def unauthorized() {
    def resp = [code: 401, message:'Unauthorized']
    withFormat {
      html {
        log.debug("Got html request..")
        forward controller: 'login', action: 'denied', params:(params)
      }
      json {
        response.sendError(401)
        render resp as JSON
      }
    }
  }
}