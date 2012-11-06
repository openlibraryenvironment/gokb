<!DOCTYPE html>
<!--[if lt IE 7 ]> <html lang="en" class="no-js ie6"> <![endif]-->
<!--[if IE 7 ]>    <html lang="en" class="no-js ie7"> <![endif]-->
<!--[if IE 8 ]>    <html lang="en" class="no-js ie8"> <![endif]-->
<!--[if IE 9 ]>    <html lang="en" class="no-js ie9"> <![endif]-->
<!--[if (gt IE 9)|!(IE)]><!--> <html lang="en" class="no-js"><!--<![endif]-->
  <head>
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge,chrome=1">
    <title><g:layoutTitle default="GoKB"/></title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="shortcut icon" href="${resource(dir: 'images', file: 'favicon.ico')}" type="image/x-icon">
    <g:layoutHead/>
    <r:layoutResources />
  </head>
  <body class="public">
    <div class="navbar navbar-fixed-top">
      <div class="navbar-inner navbar-public">
        <div class="container">
          <!--
          <img class="brand" alt="Knowledge Base + logo" src="images/gokb_logo_small.png" />
          -->
          <div class="nav-collapse">
            <ul class="nav">
              <li>
                <a href="/kbplus/"> Home </a>
              </li>
              <li class="active">
                <a href="/kbplus/about"> About KB+ </a>
              </li>
              <li>
                <a href="/kbplus/signup"> Sign Up </a>
              </li>
              <li>
                <a href="/kbplus/publicExport"> Exports </a>
              </li>
              <li class="last">
                <a href="/kbplus/contact-us"> Contact Us </a>
              </li>
            </ul>           
          </div>
        </div>           
      </div>
    </div>
    <div class="navbar-push-public"></div>

    <g:layoutBody/>

    <g:javascript library="application"/>
    <r:layoutResources />
  </body>
</html>
