package gokbg3

import java.text.MessageFormat

class MessageService {

  def messageSource

  public List processValidationErrors(errors, locale) {
    def result = []

    errors.each { eo ->


      def resolvedArgs = []
      def errorMessage = null

      eo.getArguments().each { ma ->
        log.debug("message arg type is: ${ma?.class?.name ?: 'null'}")
        if (ma && ma instanceof String) {
          String[] emptyArgs = []
          def arg = messageSource.resolveCode(ma, locale)
          
          if (arg) {
            arg.format(emptyArgs)

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
        result.add(errorMessage)
      }else{
        log.debug("No message found for ${eo.codes}")
        log.debug("Default: ${MessageFormat.format(eo.defaultMessage, messageArgs)}")
        result.add("${MessageFormat.format(eo.defaultMessage, messageArgs)}")
      }
    }
    result
  }
}