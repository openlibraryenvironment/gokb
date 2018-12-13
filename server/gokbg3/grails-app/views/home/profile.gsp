<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: My Profile</title>
  </head>
  <body>  
  <g:if test="${flash.message}">
    <br/>
    <div class="well message" role="status" style="font-size: medium;color: green;">${flash.message}</div>
  </g:if>
  <h1 class="page-header">My Profile</h1>
  <div id="mainarea" class="panel panel-default">

      <div class="panel-heading">
        <h3 class="panel-title">
          My Details
        </h3>
      </div>

      <div class="panel-body" >
        <div class="pull-right well">
          <g:link controller="home" action="sendAlerts"><button class="btn btn-success">Send Alerts Update</button></g:link>
        </div>
        <g:render template="/apptemplates/user" model="${ ["d":user] }"></g:render>
      </div>

      <div class="panel-heading">
        <h3 class="panel-title">
          Change Password
        </h3>
      </div>

      <div class="panel-body" >
	      <g:form action="changePass">
		      <dl class="dl-horizontal">
		        <dt class="dt-label">Original Password :</dt>
		        <dd><input class="form-control" name="origpass" type="password"/>
		        <dt class="dt-label">New Password :</dt>
		        <dd><input class="form-control" name="newpass" type="password"/>
		        <dt class="dt-label">Repeat New Password :</dt>
		        <dd><input class="form-control" name="repeatpass" type="password"/>
		        <dt></dt><dd><button type="submit" class="btn btn-sm">Change Password</button></dd>
		      </dl>
		      <g:if test='${flash.message}'>
		        <div class='well'>${flash.message}</div>
		      </g:if>
	      </g:form>
      </div>
  </div>
</body>
</html>
