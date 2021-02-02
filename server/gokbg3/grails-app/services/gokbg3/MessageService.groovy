package gokbg3

import org.springframework.validation.FieldError

import java.text.MessageFormat

class MessageService {

  def messageSource

  public Map processValidationErrors(errors, def locale = null) {
    def result = [:]

    errors.allErrors.each { eo ->
      log.debug("Processing ${eo} (${eo.class.name})")
      def field = 'object'
      def resolvedArgs = []
      def errorMessage = null

      locale = locale ?: new Locale('en')

      if ( eo instanceof FieldError ){
        if (!result[eo.field]) {
          result[eo.field] = []
        }
        field = eo.field
      } else if (!result['object']){
        result.object = []
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
        result[field].add([message: errorMessage, baddata: (field == 'object' ? eo.objectName : eo.rejectedValue)])
      }else{
        log.debug("No message found for ${eo.codes}")
        log.debug("Default: ${MessageFormat.format(eo.defaultMessage, messageArgs)}")
        result[field].add([message:"${MessageFormat.format(eo.defaultMessage, messageArgs)}", baddata: (field == 'object' ? eo.objectName : eo.rejectedValue)])
      }
    }
    result
  }

  def resolveCode(code, args, locale) {
    log.debug("Resolve ${code} with args ${args} (${locale})")
    def result = null
    String[] messageArgs = []

    try {
      if (args && args.size() > 0) {
        messageArgs = args
        result = messageSource.resolveCode(code, locale)?.format(messageArgs)

        if (!result) {
          log.error("Unable to resolve code ${code} for ${locale}!")
          result = messageSource.resolveCode(code, Locale.ENGLISH)?.format(messageArgs)
        }
      }
      else {
        result = messageSource.resolveCodeWithoutArguments(code, locale)

        if (!result) {
          log.error("Unable to resolve code ${code} for ${locale}!")
          result = messageSource.resolveCodeWithoutArguments(code, Locale.ENGLISH)
        }
      }
    }
    catch (Exception e) {
      log.error("Exception resolving code: $code!", e)
    }

    return result
  }
}
