<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: My Preferences</title>
  </head>
  <body>  
  <h1 class="page-header">My Preferences</h1>
   <div id="mainarea"
    class="panel panel-default">
      <div class="panel-body" >
        <dl class="dl-horizontal">

	        <dt>Show Info Icon :</dt>
	        <dd>
	          <g:xEditableRefData owner="${user}" field="showInfoIcon"
	            config="YN" />
	        </dd>

	        <dt>Show Quick View :</dt>
	        <dd>
	          <g:xEditableRefData owner="${user}" field="showQuickView" config="YN" />
	        </dd>

	        <dt>Default Page Size :</dt>
	        <dd><g:xEditable owner="${user}" field="defaultPageSize" /></dd>

	        <dt>Send Alert Emails :</dt>
	        <dd><g:xEditableRefData owner="${user}" field="send_alert_emails" config="YN" /></dd>
	      </dl>
      </div>
    </div>
</body>
</html>
