package gokbg3

import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import java.text.MessageFormat

class MessageService {

  def messageSource

  public Map processValidationErrors(errors, def locale = null) {
    def result = [:]

    errors.allErrors.each { eo ->
      def field = 'object'
      def resolvedArgs = []
      def errorMessage = null

      if ( eo instanceof FieldError ){
        if (!result[eo.field]) {
          result[eo.field] = []
        }
        field = eo.field
      }

      eo.getArguments().each { ma ->
        log.debug("message arg ${ma} type is: ${ma?.class?.name ?: 'null'}")
        if (ma && ma instanceof String) {
          String[] emptyArgs = []
          def arg = messageSource.resolveCode(ma, locale)
          
          if (arg) {
            arg = arg.format(emptyArgs)

            resolvedArgs.add(arg)
          }
        }
        else {
          resolvedArgs.add(ma)
        }
      }

      String[] messageArgs = resolvedArgs

      eo.getCodes().each { ec ->

        if (!errorMessage) {
          // log.debug("testing code -> ${ec}")

          def msg = messageSource.resolveCode(ec, locale)?.format(messageArgs)

          if(msg && msg != ec) {
            errorMessage = msg
          }

          if(!errorMessage) {
            // log.debug("Could not resolve message")
          }else{
            log.debug("found message: ${msg}")
          }
        }
      }

      if (errorMessage) {
        result[field].add([message: errorMessage, baddata: eo.rejectedValue])
      }else{
        log.debug("No message found for ${eo.codes}")
        log.debug("Default: ${MessageFormat.format(eo.defaultMessage, messageArgs)}")
        result[field].add([message:"${MessageFormat.format(eo.defaultMessage, messageArgs)}", baddata: eo.rejectedValue])
      }
    }
    result
  }
}