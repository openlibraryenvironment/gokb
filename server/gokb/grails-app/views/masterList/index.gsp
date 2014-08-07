<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKbo : Master Lists</title>
  </head>
  <body>

    <div class="container">
      <div class="row">
        <table class="table table-striped">
          <thead>
            <th>Org</th>
          </thead>
          <tbody>
            <g:each in="${orgs}" var="org">
              <tr>
                <td><g:link controller="MasterList" action="org" id="${org.id}">${org.name}</g:link></td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </div>
    </div>

  </body>
</html>
