package gokbg3


import grails.config.Config
import grails.core.support.GrailsConfigurationAware
import grails.gsp.PageRenderer
import grails.plugin.springsecurity.authentication.dao.NullSaltSource
import grails.plugin.springsecurity.ui.strategy.MailStrategy
import grails.plugin.springsecurity.ui.strategy.PropertiesStrategy
import grails.plugin.springsecurity.ui.strategy.RegistrationCodeStrategy
import grails.plugin.springsecurity.ui.RegisterCommand
import grails.plugin.springsecurity.ui.ForgotPasswordCommand
import grails.plugin.springsecurity.ui.RegistrationCode
import groovy.text.SimpleTemplateEngine
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder
import org.springframework.security.authentication.dao.SaltSource
import org.springframework.security.access.annotation.Secured;

import groovy.util.logging.*

@Slf4j
@Secured(['IS_AUTHENTICATED_ANONYMOUSLY'])
class RegisterController extends grails.plugin.springsecurity.ui.RegisterController {

  static defaultAction = 'register'

  /** Dependency injection for the 'saltSource' bean. */
  SaltSource saltSource

  /** Dependency injection for the 'uiMailStrategy' bean. */
  MailStrategy uiMailStrategy

  /** Dependency injection for the 'uiRegistrationCodeStrategy' bean. */
  RegistrationCodeStrategy uiRegistrationCodeStrategy

	/** Dependency injection for the 'uiPropertiesStrategy' bean. */
	PropertiesStrategy uiPropertiesStrategy

  String serverURL

  MessageSource messageSource

  def sessionFactory

  def springSecurityService

	static final String EMAIL_LAYOUT = "/layouts/email"
	static final String FORGOT_PASSWORD_TEMPLATE = "/register/_forgotPasswordMail"
	static final String VERIFY_REGISTRATION_TEMPLATE = "/register/_verifyRegistrationMail"

  @Override
  def register(RegisterCommand registerCommand) {

    def secResult
    def errors = [:]

    if ( !request.post ) {
      session.secQuestion = "${new Random().next(2) + 1}*${new Random().next(2) + 1}"
      return [registerCommand: new RegisterCommand(), secQuestion: session.secQuestion]
    }

    if ( session.regTries && session.regTries > 3 ) {
      response.setStatus(429)

      return [registerCommand: registerCommand, noTries: true, errors: processErrors(registerCommand.errors)]
    }

    def secTerms = session.secQuestion ? session.secQuestion.split("\\*") : null
    
    if ( secTerms ) {
      secResult = (secTerms[0] as Integer) * (secTerms[1] as Integer)
    }

    if ( !secTerms || params.int('secAnswer') != secResult ) {
      session.secQuestion = "${new Random().next(2) + 1}*${new Random().next(2) + 1}"
      session.regTries = session.regTries ? (session.regTries + 1) : 1
      return [registerCommand: registerCommand, secFailed: true, secQuestion: session.secQuestion, errors: processErrors(registerCommand.errors)]
    }

    if (registerCommand.hasErrors() || params.phone) {
      session.secQuestion = "${new Random().next(2) + 1}*${new Random().next(2) + 1}"
      return [registerCommand: registerCommand, secQuestion: session.secQuestion, errors: processErrors(registerCommand.errors)]
    }

    def user = uiRegistrationCodeStrategy.createUser(registerCommand)
    String salt = saltSource instanceof NullSaltSource ? null : registerCommand.username
    RegistrationCode registrationCode = uiRegistrationCodeStrategy.register(user, registerCommand.password, salt)

    if (registrationCode == null || registrationCode.hasErrors()) {
      // null means problem creating the user
      flash.error = message(code: 'spring.security.ui.register.miscError')
      return [registerCommand: registerCommand]
    }

    session.secQuestion = null
    session.regTries = 0

    if( requireEmailValidation ) {
      if ( registerCommand.email ) {
        sendVerifyRegistrationMail registrationCode, user, registerCommand.email
        [emailSent: true, registerCommand: registerCommand]
      }
      else {
        [emailSent: true, registerCommand: registerCommand, noAddress: true]
      }
    } else {
      redirectVerifyRegistration(uiRegistrationCodeStrategy.verifyRegistration(registrationCode.token))
    }
  }

  def verifyRegistration() {

    String token = params.t

    RegistrationCode registrationCode = token ? RegistrationCode.findByToken(token) : null
    if (!registrationCode) {
      flash.error = message(code: 'spring.security.ui.register.badCode')
      redirect uri: successHandlerDefaultTargetUrl
      return
    }

    def user = uiRegistrationCodeStrategy.finishRegistration(registrationCode)

    if (!user) {
      flash.error = message(code: 'spring.security.ui.register.badCode')
      redirect uri: successHandlerDefaultTargetUrl
      return
    }

    if (user.hasErrors()) {
      // expected to be handled already by ErrorsStrategy.handleValidationErrors
      return
    }

    springSecurityService.reauthenticate user.username

    flash.message = message(code: 'spring.security.ui.register.complete')
    redirect uri: registerPostRegisterUrl ?: successHandlerDefaultTargetUrl
  }

  @Override
  protected void sendVerifyRegistrationMail(RegistrationCode registrationCode, user, String email) {
      String url = super.generateLink('verifyRegistration', [t: registrationCode.token])

      String body = super.renderRegistrationMailBody(url, user)

      uiMailStrategy.sendVerifyRegistrationMail(
              to: email,
              from: registerEmailFrom,
              subject: registerEmailSubject,
              html: body
      )
  }

  private Map processErrors(errors) {
    def result = ['username': [], 'email': [], 'password': [], 'password2':[]]

    result.each { fn, val ->
      def fieldErrors = errors.getFieldErrors(fn)

      fieldErrors.each { eo ->

        def resolvedArgs = []
        def errorMessage = null

        eo.getArguments().each { ma ->
          if (ma && ma instanceof String) {
            String[] emptyArgs = []
            def arg = messageSource.resolveCode(ma, request.locale)

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

            def msg = messageSource.resolveCode(ec, request.locale)?.format(messageArgs)

            if(msg && msg != ec) {
              errorMessage = msg
            }

            if(!errorMessage) {
              // log.debug("Could not resolve message")
            }else{
              // log.debug("found message: ${msg}")
            }
          }
        }

        if (errorMessage) {
          result[fn].add(errorMessage)
        }else{
          log.debug("No message found for ${eo.codes}")
          log.debug("Default: ${MessageFormat.format(eo.defaultMessage, messageArgs)}")
          result[fn].add("${MessageFormat.format(eo.defaultMessage, messageArgs)}")
        }
      }
    }
    result
  }

  @Override
	def forgotPassword(ForgotPasswordCommand forgotPasswordCommand) {

		if (!request.post) {
			return [forgotPasswordCommand: new ForgotPasswordCommand()]
		}

		if (forgotPasswordCommand.hasErrors()) {
			return [forgotPasswordCommand: forgotPasswordCommand]
		}

		def user = findUserByUsername(forgotPasswordCommand.username)
		if (!user) {
			forgotPasswordCommand.errors.rejectValue 'username', 'spring.security.ui.forgotPassword.user.notFound'
			return [forgotPasswordCommand: forgotPasswordCommand]
		}

		String email = uiPropertiesStrategy.getProperty(user, 'email')
		if (!email) {
			forgotPasswordCommand.errors.rejectValue 'username', 'spring.security.ui.forgotPassword.noEmail'
			return [forgotPasswordCommand: forgotPasswordCommand]
		}

		uiRegistrationCodeStrategy.sendForgotPasswordMail(
				forgotPasswordCommand.username, email) { String registrationCodeToken ->

			String url = generateLink('resetPassword', [t: registrationCodeToken])
			String body = forgotPasswordEmailBody

			if (!body) {
				body = renderEmail(
						FORGOT_PASSWORD_TEMPLATE, EMAIL_LAYOUT,
						[
								url     : url,
								username: user.username
						]
				)
			} else if (body.contains('$')) {
				body = evaluate(body, [user: user, url: url])
			}

			body
		}

		[emailSent: true, forgotPasswordCommand: forgotPasswordCommand]
	}

  protected String forgotPasswordEmailBody
  protected Boolean requireForgotPassEmailValidation
  protected List<HashMap> forgotPasswordExtraValidation
  protected String forgotPasswordExtraValidationDomainClassName
  protected String registerEmailBody
  protected String registerEmailFrom
  protected String registerEmailSubject
  protected String registerPostRegisterUrl
  protected String registerPostResetUrl
  protected String successHandlerDefaultTargetUrl
  protected Boolean requireEmailValidation
  protected static int passwordMaxLength
  protected String validationUserLookUpProperty
  protected static int passwordMinLength
  protected static String passwordValidationRegex

  @Override
  void afterPropertiesSet() {
    super.afterPropertiesSet()

    RegisterCommand.User = User
    RegisterCommand.usernamePropertyName = usernamePropertyName

    forgotPasswordEmailBody = conf.ui.forgotPassword.emailBody ?: ''
    requireForgotPassEmailValidation = conf.ui.forgotPassword.requireForgotPassEmailValidation instanceof groovy.util.ConfigObject ? true : Boolean.valueOf(conf.ui.forgotPassword.requireForgotPassEmailValidation)
    forgotPasswordExtraValidation = conf.ui.forgotPassword.forgotPasswordExtraValidation  ?: []
    forgotPasswordExtraValidationDomainClassName = (conf.ui.forgotPassword.forgotPasswordExtraValidationDomainClassName ?: '').toString().trim()
    registerEmailBody = conf.ui.register.emailBody ?: ''
    registerEmailFrom = conf.ui.register.emailFrom ?: ''
    validationUserLookUpProperty = conf.ui.forgotPassword.validationUserLookUpProperty ?: 'user'
    registerEmailSubject = conf.ui.register.emailSubject ?: messageSource ? messageSource.getMessage('spring.security.ui.register.email.subject', [].toArray(), 'New Account', LocaleContextHolder.locale) : '' ?: ''
    registerPostRegisterUrl = conf.ui.register.postRegisterUrl ?: ''
    registerPostResetUrl = conf.ui.forgotPassword.postResetUrl ?: ''
    successHandlerDefaultTargetUrl = conf.successHandler.defaultTargetUrl ?: '/'
    requireEmailValidation = conf.ui.register.requireEmailValidation instanceof groovy.util.ConfigObject ? true : Boolean.valueOf(conf.ui.register.requireEmailValidation)
    passwordMaxLength = conf.ui.password.maxLength instanceof Number ? conf.ui.password.maxLength : 64
    passwordMinLength = conf.ui.password.minLength instanceof Number ? conf.ui.password.minLength : 8
    passwordValidationRegex = conf.ui.password.validationRegex ?: '^.*(?=.*\\d)(?=.*[a-zA-Z])(?=.*[!@#$%^&]).*$'
  }
}
