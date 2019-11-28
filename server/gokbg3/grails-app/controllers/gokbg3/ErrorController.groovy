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
      log.debug("Rendering HTML 404")
      redirect (uri:'/notFound', params:[status:404])
    }
    render resp as JSON
  }
  
  def forbidden() {
    def resp = [code: 403, message:'Forbidden']
    withFormat {
      html {
        log.debug("Rendering HTML 403")
        redirect (uri:'/login/denied', params:[status:403])
      }
      json {
        log.debug("Rendering JSON 403")
        response.sendError(403)
        render resp as JSON
      }
    }
  }

  def unauthorized() {
    def resp = [code: 401, message:'Unauthorized']
    withFormat {
      html {
        log.debug("Rendering HTML 401")
        forward controller: 'login', action: 'denied', params:(params)
      }
      json {
        log.debug("Rendering JSON 401")
        response.sendError(401)
        render resp as JSON
      }
    }
  }

  def badRequest() {
    def resp = [code: 400, message:'Bad Request']
    withFormat {
      html {
        log.debug("Rendering HTML 400")
        forward controller: 'login', action: 'denied', params:(params)
      }
      json {
        log.debug("Rendering JSON 400")
        response.sendError(400)
        render resp as JSON
      }
    }
  }
}