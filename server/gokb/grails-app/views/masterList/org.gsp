<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKbo : Master Lists</title>
  </head>
  <body>

    <div class="container-fluid">
      <div class="row-fluid">
        <table class="table table-striped">
          <thead>
            <th>Org</th>
          </thead>
          <tbody>
            <g:each in="${titles}" var="title">
              <tr>
                <td>${title.name}</td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </div>
    </div>

  </body>
</html>
