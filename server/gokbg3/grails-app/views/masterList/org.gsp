<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <asset:stylesheet src="gokb/application.css"/>
    <asset:javascript src="gokb/application.js" />
    <title>GOKb: Master Lists</title>
  </head>
  <body>
	  <h1 class="page-header">
	    Master lists for <g:render template="/apptemplates/component_heading" model="${[d:o]}" />
	  </h1>
    <div id="mainarea" class="panel panel-default">
      <div class="panel-body">
        <table class="table table-striped">
          <thead>
            <tr>
              <th>Package</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${org_packages}" var="pkg">
              <tr>
                <td>${pkg.name}</td>
              </tr>
            </g:each>
          </tbody>
        </table>

        <table class="table table-striped">
          <thead>
            <tr>
	            <th></th>
	            <th>Titles</th>
	            <th>Package</th>
	            <th>Platform</th>
	            <th>Start</th>
	            <th>End</th>
	          </tr>
          </thead>
          <tbody>
            <g:each in="${tipps}" var="tipp">
              <tr>
                <td><g:link controller="resource" action="show" id="${tipp.class.name}:${tipp.id}">tipp ${tipp.id}</g:link></td>
                <td>${tipp.title.name}</td>
                <td>${tipp.pkg.name}</td>
                <td>${tipp.hostPlatform.name}</td>
                <td>Start Date: ${tipp.startDate}<br/>Start Volume: ${tipp.startVolume}<br/>Start Issue: ${tipp.startIssue}</td>
                <td>End Date: ${tipp.endDate}<br/>End Volume: ${tipp.endVolume}<br/>End Issue: ${tipp.endIssue}</td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </div>
    </div>
  </body>
</html>
