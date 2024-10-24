<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Title Change</title>
</head>
<body>
  <g:form controller="workflow" action="editTitleChange" id="${params.id}">
    <h1 class="page-header">Title Change</h1>
    <div id="mainarea" class="panel panel-default">
      <div class="panel-heading">
        <h3 class="panel-title">Step 2 of 2</h3>
      </div>
      <div class="panel-body">
        <h3>
          ${d.activityName} - ${d.status?.value}
        </h3>
        <table class="table table-bordered no-select-all">
          <thead>
            <tr>
              <th rowspan="2">Type</th>
              <th>Title</th>
              <th>Package</th>
              <th>Platform</th>
              <th>Start Date</th>
              <th>Start Volume</th>
              <th>Start Issue</th>
              <th>End Date</th>
              <th>End Volume</th>
              <th>End Issue</th>
              <th>Retire</th>
              <th>Review</th>
            </tr>
            <tr>
              <th colspan="11">URL</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${tipps}" var="tipp">
              <g:if test="${tipp.type=='CURRENT'}">
                <tr style="background-color: #FF4D4D;">
                  <td rowspan="2"> ${tipp.type} </td>
                  <td> ${tipp.title?tipp.title.name:tipp.name} </td>
                  <td> ${tipp.pkg.name} </td>
                  <td> ${tipp.hostPlatform.name} </td>
                  <td> <input name="_oldtipp:${tipp.id}:startDate" type="date" value="${tipp.startDate}"/> </td>
                  <td> <input type="text" name="_oldtipp:${tipp.id}:startVolume" value="${tipp.startVolume}" style="width:40px;"/> </td>
                  <td> <input type="text" name="_oldtipp:${tipp.id}:startIssue" value="${tipp.startIssue}" style="width:40px;"/> </td>
                  <td> <input name="_oldtipp:${tipp.id}:endDate" type="date" value="${tipp.endDate}"/> </td>
                  <td> <input type="text" name="_oldtipp:${tipp.id}:endVolume" value="${tipp.endVolume}" style="width:40px;"/> </td>
                  <td> <input type="text" name="_oldtipp:${tipp.id}:endIssue" value="${tipp.endIssue}" style="width:40px;"/> </td>
                  <td> <input name="oldtipp_close:${tipp.id}" type="checkbox" ${params["oldtipp_close:${tipp.id}"]=='on'?'checked':''} /></td>
                  <td> <input name="oldtipp_review:${tipp.id}" type="checkbox"  ${params["oldtipp_review:${tipp.id}"]=='on'?'checked':''} /></td>
                </tr>
                <tr>
                  <td colspan="11"> ${tipp.url ?: 'TIPP URL not present'} </td>
                </tr>
              </g:if>
              <g:else>
                <tr style="background-color: #4DFF4D;">
                  <td rowspan="2"> ${tipp.type} </td>
                  <td> ${tipp.name?:tipp.title?tipp.title.name:""} </td>
                  <td> ${tipp.pkg.name} </td>
                  <td> ${tipp.hostPlatform.name} </td>
                  <td> <input type="date" name="_tippdata:${tipp.parent}:${tipp.seq}:startDate" value="${tipp.startDate}"/> </td>
                  <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:startVolume" value="${tipp.startVolume}" style="width:40px;"/> </td>
                  <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:startIssue" value="${tipp.startIssue}" style="width:40px;"/> </td>
                  <td> <input type="date" name="_tippdata:${tipp.parent}:${tipp.seq}:endDate" value="${tipp.endDate}"/> </td>
                  <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:endVolume" value="${tipp.endVolume}" style="width:40px;"/> </td>
                  <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:endIssue" value="${tipp.endIssue}" style="width:40px;"/> </td>
                  <td> <button type="submit" name="remove" value="${tipp.parent}:${tipp.seq}" class="btn btn-warn">Remove</button></td>
                  <td> <input name="_tippdata:${tipp.parent}:${tipp.seq}:review" type="checkbox" ${tipp.review=='on'?'checked':''} /></td>
                </tr>
                <tr>
                  <td colspan="11"> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:url" value="${tipp.url}"/> </td>
                </tr>
              </g:else>
            </g:each>
          </tbody>
        </table>
      </div>

      <div class="btn-group clearfix pull-right">
        <button type="submit" class="btn btn-default btn-success btn-sm pull-right" name="update" value="update">Update</button>
        <button type="submit" class="btn btn-default btn-success btn-sm pull-right" name="process" value="process">Process Change</button>
        <button type="submit" class="btn btn-default btn-danger btn-sm " name="abandon" value="abandon">Abandon Change</button>
      </div>

    </div>
  </g:form>

  <pre>
    ${d.activityData}
  </pre>
  <ul>
  <g:each in="${tipps}" var="tipp">
    <li>${tipp}</li>
  </g:each>
  </ul>

</body>
</html>

