<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Title Change</title>
</head>
<body>

  <h1 class="page-header">Title Transfer</h1>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Step 1 of 2</h3>
    </div>
    <div class="panel-body">
      <g:form controller="workflow" action="startTitleChange"
        method="get">
        <div class="row">
          <div class="col-md-6">
            <h3>Title to change</h3>
            <table class="table table-striped table-bordered no-select-all">
              <thead>
                <tr>
                  <th></th>
                  <th>Title(s)</th>
                </tr>
              </thead>
              <tbody>
                <g:each in="${objects_to_action}" var="o">
                  <tr>
                    <td><input type="checkbox" name="tt:${o.id}"
                      checked="checked" /></td>
                    <td>
                      ${o.name} (Currently : ${o.currentPublisher?.name})
                    </td>
                </g:each>
              </tbody>
            </table>
          </div>
          <div class="col-md-6">
            <h3>Change :</h3>
            <label>Change Type:</label>
            <select name="changeType">
              <option value="nameChange">Simple Name Change</option>
            </select>
          </div>

        </div>
        <button type="submit" class="btn btn-default btn-sm pull-right">Next</button>
      </g:form>
    </div>
  </div>
</body>
</html>

