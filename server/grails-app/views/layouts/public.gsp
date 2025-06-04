<!DOCTYPE html>
<html lang="en">

<head>

    <meta charset="utf-8"/>
    <meta http-equiv="X-UA-Compatible" content="IE=edge"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <meta name="description" content=""/>
    <meta name="author" content=""/>
    <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon"/>
    <asset:script> var contextPath="${grailsApplication.config.getProperty('server.servlet.context-path') ?: '/'}"; </asset:script>
    <g:layoutHead />

    <asset:stylesheet src="gokb/themes/${ grailsApplication.config.getProperty('gokb.theme') }/bw-theme.css"/>
    <asset:stylesheet src="gokb/fontawesome.css" />
    <asset:stylesheet src="gokb/application.css"/>
    <asset:stylesheet src="gokb/gokb-brand.css"/>

    <title><g:layoutTitle default="GOKB: Welcome" /></title>
</head>

<body id="page-body" class="theme-${ grailsApplication.config.getProperty('gokb.theme') }">

  <nav class="navbar navbar-default" id="primary-nav-bar" role="navigation" style="background-color:${grailsApplication.config.getProperty('gokb.themeColor')}">
      <!-- Brand and toggle get grouped for better mobile display -->
      <div class="navbar-header">
        <button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#primary-nav">
          <span class="sr-only">Toggle navigation</span>
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
          <span class="icon-bar"></span>
        </button>
        <!-- navbar-brand is hidden on larger screens, but visible when the menu is collapsed -->
        <a class="navbar-brand" href="${grailsApplication.config.getProperty('server.contextPath') ?: ''}/">
          ${grailsApplication.config.getProperty('gokb.appname') ?: 'GOKB'}
          <g:if test="${grailsApplication.config.getProperty('gokb.instance.description')}">
            – ${grailsApplication.config.getProperty('gokb.instance.description')}
          </g:if>
        </a>
      </div>

      <div class="nav navbar-nav navbar-right">
          <g:if test="${grailsApplication.config.getProperty('gokb.blogUrl')}">
            <li><a  style="font-weight:bold;" href ="${grailsApplication.config.getProperty('gokb.blogUrl')}">About GOKB</a></li>
          </g:if>
          <li><a href="https://github.com/openlibraryenvironment/gokb/wiki/API"  style="font-weight:bold;">API Documentation</a></li>
          <li><span style="width:15px"></span></li>
          <li><g:link controller="register" action="register" style="font-weight:bold;">Register</g:link></li>
          <li><g:link controller="home" action="index" style="font-weight:bold;">Legacy UI</g:link></li>
          <g:if test="${grailsApplication.config.getProperty('gokb.uiUrl')}">
            <li><a style="font-weight:bold;" href ="${grailsApplication.config.getProperty('gokb.uiUrl')}">GOKB Client</a></li>
          </g:if>
      </div>
   </nav>




    <g:layoutBody />

    <footer>
        <div class="container">
            <div class="row">
                <div class="col-lg-12 text-center">
                  <g:if test="${grailsApplication.config.getProperty('gokb.accessibilityUrl')}">
                    <span>
                      <a href="${grailsApplication.config.getProperty('gokb.accessibilityUrl')}">Accessibility</a>
                    </span>
                    |
                  </g:if>
                  <g:if test="${grailsApplication.config.getProperty('gokb.privacyUrl')}">
                    <span>
                      <a href="${grailsApplication.config.getProperty('gokb.privacyUrl')}">Privacy Policy</a>
                    </span>
                    |
                  </g:if>
                  <g:if test="${grailsApplication.config.getProperty('gokb.imprintUrl')}">
                    <span>
                      <a href="${grailsApplication.config.getProperty('gokb.imprintUrl')}">Imprint</a>
                    </span>
                  </g:if>
                </div>
            </div>
        </div>
    </footer>

</body>

</html>
