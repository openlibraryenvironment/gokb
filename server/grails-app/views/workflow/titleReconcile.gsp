<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb::Title Reconcile</title>
</head>
<body>
	<div class="container">
		<h1>Collecting Data....</h1>
		<ul>
			<g:each in="${objects_to_action}" var="ota">
				<li id="title:${ota.id}">
					${ota.name}
				</li>
			</g:each>
		</ul>
	</div>

	<g:javascript>
      var list = [
        <g:each in="${objects_to_action}" var="ota">
          { oid:"${ota.class.name}:${ota.id}",issns:[
            <g:each in="${ota.ids}" var="id">
              { namespace:"${id.namespace.value}", value:"${id.value}" },
            </g:each>
          ] },
        </g:each>
      ]

      $(document).ready(function() {
        for (var i = 0; i < list.length; i++) {
          $.ajax({
            // libs and culture: 0894-8631
            url: "http://xissn.worldcat.org/webservices/xid/issn/0894-8631?method=getHistory&format=json&fl=form",
            dataType:"jsonp",
            crossDomain: true
          }).done(function(data) {
            alert(data);
          });
        }
      });
    </g:javascript>
</body>
</html>

