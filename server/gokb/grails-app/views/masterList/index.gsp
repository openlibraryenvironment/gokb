<!DOCTYPE html>
<html>
<head>
	<meta name="layout" content="sb-admin" />
	<title>GOKb: Master Lists</title>
</head>
<body>
  <h1 class="page-header">Master Lists</h1>
  <div id="mainarea"
   class="panel panel-default">
     <table class="table table-striped">
        <thead>
          <th>Org</th>
        </thead>
        <tbody>
          <g:each in="${orgs}" var="org">
            <tr>
              <td><g:link controller="MasterList" action="org"
                  id="${org.id}">
                  ${org.name}
                </g:link></td>
            </tr>
          </g:each>
        </tbody>
      </table>
   </div>
</body>
</html>
