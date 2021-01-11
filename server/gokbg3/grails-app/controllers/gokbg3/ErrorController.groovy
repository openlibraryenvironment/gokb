package gokbg3

import grails.converters.JSON
import grails.util.Environment
import groovy.util.logging.*
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.acls.model.NotFoundException

@Slf4j
class ErrorController {
  def springSecurityService

  def index() {
    
  }

  def serverError() {
    def resp = [code: 500, message:'Server Error']
    def exception = request.exception?.cause ?: null

    if ( exception && Environment.current == Environment.DEVELOPMENT ) {
      resp.exception = exception
    }

    if ( request.forwardURI.contains('/rest/') ) {
      log.debug("Rendering JSON REST 500 (${request.forwardURI})")
      response.setStatus(500)
      render resp as JSON
    }
    else {
      withFormat {
        html {
          forward (uri:'/error')
        }
        json {
          response.setStatus(500)
          render resp as JSON
        }
      }
    }
  }

  def wrongMethod() {
    def resp = [code: 405, message:'Method not allowed']

    if(request.forwardURI.contains('/rest/')) {
      log.debug("Rendering JSON REST 405 (${request.forwardURI})")
      response.setStatus(405)
      render resp as JSON
    }
    else {
      withFormat {
        html {
          forward (uri:'/error')
        }
        json {
          response.setStatus(405)
          render resp as JSON
        }
      }
    }
  }

  def notFound() {
    def resp = [code: 404, message:'Not Found']

    if(request.forwardURI.contains('/rest/')) {
      log.debug("Rendering JSON REST 404 (${request.forwardURI})")
      response.setStatus(404)
      render resp as JSON
    }
    else{
      withFormat {
        html {
          log.debug("Rendering HTML 404 (${request.forwardURI})")
          forward (uri:'notFound', params:[status:404])
        }
        json {
          response.setStatus(404)
          render resp as JSON
        }
      }
    }
  }
  
  def forbidden() {
    def resp = [code: 403, message:'Forbidden']

    if(request.forwardURI.contains('/rest/')) {
      log.debug("Rendering JSON REST 403 (${request.forwardURI})")
      response.setStatus(403)
      render resp as JSON
    }
    else {
      withFormat {
        html {
          log.debug("Rendering HTML 403 (${request.forwardURI})")
          redirect (uri:'/login/denied', params:[status:403])
        }
        json {
          log.debug("Rendering JSON 403 (${request.forwardURI})")
          response.setStatus(403)
          render resp as JSON
        }
      }
    }
  }

  def unauthorized() {
    def resp = [code: 401, message:'Unauthorized']

    if(request.forwardURI.contains('/rest/')) {
      log.debug("Rendering JSON REST 401 (${request.forwardURI})")
      response.setStatus(401)
      render resp as JSON
    }
    else{
      withFormat {
        html {
          log.debug("Rendering HTML 401 (${request.forwardURI})")
          redirect controller: 'login', action: 'auth', params:(params)
        }
        json {
          log.debug("Rendering JSON 401 (${request.forwardURI})")
          response.setStatus(401)
          render resp as JSON
        }
      }
    }
  }

  def badRequest() {
    def resp = [code: 400, message:'Bad Request']

    if(request.forwardURI.contains('/rest/')) {
      log.debug("Rendering JSON REST 400 (${request.forwardURI})")
      response.setStatus(400)
      render resp as JSON
    }
    else {
      withFormat {
        html {
          log.debug("Rendering HTML 400 (${request.forwardURI})")
          forward controller: 'login', action: 'denied', params:(params)
        }
        json {
          log.debug("Rendering JSON 400 (${request.forwardURI})")
          response.setStatus(400)
          render resp as JSON
        }
      }
    }
  }
}