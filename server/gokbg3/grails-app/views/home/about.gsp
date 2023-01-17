<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: About</title>
  </head>
  <body>
	 <h1 class="page-header">About GOKb</h1>
   <div id="mainarea"
		class="panel panel-default">
			<div class="panel-heading">
				<h3 class="panel-title">
					Application Info
				</h3>
			</div>
	  	<table class="table table-bordered">
              <tr><th>Git Branch</th><td> <g:meta name="build.git.branch"/></td></tr>
              <tr><th>Git Commit</th><td> <g:meta name="build.git.revision"/></td></tr>
	      <tr><th>App version</th><td> <g:meta name="info.app.version"/></td></tr>
	      <tr><th>App name</th><td> <g:meta name="info.app.name"/></td></tr>
	      <tr><th>Grails version</th><td> <g:meta name="info.app.grailsVersion"/></td></tr>
	      <tr><th>Groovy version</th><td> ${GroovySystem.getVersion()}</td></tr>
	      <tr><th>Environment</th><td> <g:meta name="grails.env" /></td></tr>
	      <tr><th>JVM version</th><td> ${System.getProperty('java.version')}</td></tr>
	      <tr><th>Reloading active</th><td> ${grails.util.Environment.reloadingAgentEnabled}</td></tr>
	      <tr><th>Build Date</th><td> <g:meta name="build.time"/></td></tr>
	      <tr><th>ES Index</th><td>${grailsApplication.config.getProperty('gokb.es.indices', Map).values().join(", ")}</td></tr>
		  <tr><th>ES Automated Indexing </th><td>Enabled: ${grailsApplication.config.getProperty('gokb.ftupdate_enabled')}</td></tr>
		  <tr><th>Package Caching</th><td>Enabled: ${grailsApplication.config.gokb.getProperty('packageOaiCaching.enabled')} - Path: ${grailsApplication.config.getProperty('gokb.packageXmlCacheDirectory')}</td></tr>
		  <tr><th>EZB Public Collections Import</th><td>Enabled: ${grailsApplication.config.getProperty('gokb.ezbOpenCollections.enabled')}</td></tr>
		  <tr><th>Automated Package Updates</th><td>Enabled: ${grailsApplication.config.getProperty('gokb.packageUpdate.enabled')}</td></tr>
		  <tr><th>ZDB Title Augmenting</th><td>Enabled: ${grailsApplication.config.getProperty('gokb.zdbAugment.enabled')} - Curator: ${grailsApplication.config.getProperty('gokb.zdbAugment.rrCurators')}</td></tr>
		  <tr><th>EZB Title Augmenting</th><td>Enabled: ${grailsApplication.config.getProperty('gokb.ezbAugment.enabled')} - Curator: ${grailsApplication.config.getProperty('gokb.ezbAugment.rrCurators')}</td></tr>
	    </table>
  	</div>
  </body>
</html>
