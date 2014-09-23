<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: My Profile</title>
  </head>
  <body>  
  <h1 class="page-header">My Profile</h1>
   <div id="mainarea"
    class="panel panel-default">
      <div class="panel-heading">
        <h3 class="panel-title">
          My Details
        </h3>
      </div>
      <div class="panel-body" >
        <g:render template="user" contextPath="../apptemplates/" model="${ ["d":user] }"></g:render>
      </div>
      <div class="panel-heading">
        <h3 class="panel-title">
          Change Password
        </h3>
      </div>
      <div class="panel-body" >
	      <g:form action="changePass">
		      <dl class="dl-horizontal">
		        <dt>Original Password :</dt>
		        <dd><input name="origpass" type="password"/>
		        <dt>New Password :</dt>
		        <dd><input name="newpass" type="password"/>
		        <dt>Repeat New Password :</dt>
		        <dd><input name="repeatpass" type="password"/>
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
