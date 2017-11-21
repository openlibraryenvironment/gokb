<!doctype html>
<html>
  <head>
    <meta name="layout" content="bootstrap"/>
    <title>Grails Twitter Bootstrap Scaffolding</title>
  </head>

  <body>
    <div class="row-fluid">
      <aside id="application-status" class="span3">
        <div class="well sidebar-nav">
          <h5>Application Status</h5>
          <ul>
            <li>App version: <g:meta name="app.version"/></li>
            <li>Grails version: <g:meta name="app.grails.version"/></li>
            <li>Groovy version: ${org.codehaus.groovy.runtime.InvokerHelper.getVersion()}</li>
            <li>JVM version: ${System.getProperty('java.version')}</li>
            <li>Controllers: ${grailsApplication.controllerClasses.size()}</li>
            <li>Domains: ${grailsApplication.domainClasses.size()}</li>
            <li>Services: ${grailsApplication.serviceClasses.size()}</li>
            <li>Tag Libraries: ${grailsApplication.tagLibClasses.size()}</li>
          </ul>
          <h5>Installed Plugins</h5>
          <ul>
            <g:each var="plugin" in="${applicationContext.getBean('pluginManager').allPlugins}">
              <li>${plugin.name} - ${plugin.version}</li>
            </g:each>
          </ul>
        </div>
      </aside>

      <section id="main" class="span9">

        <div class="hero-unit">
          <h1>Welcome to Grails</h1>

          <p>Grails scaffolding with a <a href="http://twitter.github.com/bootstrap" rel="external"><em>Twitter
          Bootstrap</em></a> look &amp; feel?</p>
          
          <p>This is a demo of how to reskin Grails dynamic scaffolding pages. I've
          used The <a href="http://freeside.co/grails-fields">Fields plugin</a> for customizing
          form rendering and the <a href="https://github.com/groovydev/twitter-bootstrap-grails-plugin">Twitter
          Bootstrap Resources plugin</a> to provide the CSS resources. Beyond that it&apos;s a
          bare Grails app using dynamically scaffolded controllers and views.</p>
        </div>
          
      </section>
    </div>
  </body>
</html>
