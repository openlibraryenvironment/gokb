<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="sb-admin"/>
    <title>GOKb: About</title>
  </head>
  <body>
   <h1 class="page-header">About GOKb</h1>
   <div id="mainarea" class="panel panel-default">
      <div class="panel-heading">
        <h3 class="panel-title">
          Release Notes
        </h3>
      </div>
        <ul>
          <li>
            <h2>3.0.5</h2>
            <ul>
              <li>Upgraded Elasticsearch Dependency to 1.3.2</li>
              <li>Removed ability to drop ES indexes from app, moved into script. Full reset still submits everything from day zero</li>
              <li>Added minimal pagination to global search, tidy global search display somewhat</li>
              <li>Corrected skos::altLable handling in orgs import</li>
            </ul>
          </li>
        </ul>
    </div>
  </body>
</html>
