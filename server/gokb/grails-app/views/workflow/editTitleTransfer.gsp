<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Title Transfer</title>
</head>
<body>
  <g:form controller="workflow" action="editTitleTransfer"
    id="${params.id}">
    <h1 class="page-header">Title Transfer</h1>
    <div id="mainarea" class="panel panel-default">
      <div class="panel-heading">
        <h3 class="panel-title">Step 2 of 2</h3>
      </div>
      <div class="panel-body">
        <h3>
          ${d.activityName}
          -
          ${d.status?.value}
        </h3>
        <p>The following titles:</p>
        <ul>
          <g:each in="${titles}" var="title">
            <li>
              ${title.name}
            </li>
          </g:each>
        </ul>
        <p>
          Will be transferred from their current publisher to
          ${newPublisher.name}. <span style="background-color: #FF4D4D;">Current
            tipps shown with a red background</span> will be deprecated. <span
            style="background-color: #11bb11;">New tipps with a green
            background</span> will be created by this transfer.
        </p>
      </div>
      <table class="table table-bordered no-select-all">
        <thead>
          <tr>
            <th rowspan="2">Select</th>
            <th>Type</th>
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
            <th colspan="12">URL</th>
          </tr>
        </thead>
        <tbody>
          <g:each in="${tipps}" var="tipp">
          <g:if test="${tipp.type=='CURRENT'}">
            <tr style="background-color: #FF4D4D;">
              <td rowspan="2"> <input name="addto-${tipp.id}" type="checkbox" value="on"/> </td>
              <td> ${tipp.type} </td>
              <td> ${tipp.title.name} </td>
              <td> ${tipp.pkg.name} </td>
              <td> ${tipp.hostPlatform.name} </td>
              <td> 
                <input name="_oldtipp:${tipp.id}:startDate" type="date" value="${tipp.startDate}"/> 
              </td>
              <td> <input type="text" name="_oldtipp:${tipp.id}:startVolume" value="${tipp.startVolume}" style="width:40px;"/> </td>
              <td> <input type="text" name="_oldtipp:${tipp.id}:startIssue" value="${tipp.startIssue}" style="width:40px;"/> </td>
              <td> <input name="_oldtipp:${tipp.id}:endDate" type="date" value="${tipp.endDate}"/> </td>
              <td> <input type="text" name="_oldtipp:${tipp.id}:endVolume" value="${tipp.endVolume}" style="width:40px;"/> </td>
              <td> <input type="text" name="_oldtipp:${tipp.id}:endIssue" value="${tipp.endIssue}" style="width:40px;"/> </td>
              <td> <input name="oldtipp_close:${tipp.id}" type="checkbox" value="on" ${params["oldtipp_close:${tipp.id}"]=='on'?'checked':''} /></td>
              <td> <input name="oldtipp_review:${tipp.id}" type="checkbox" value="on"  ${params["oldtipp_review:${tipp.id}"]=='on'?'checked':''} /></td>
            </tr>
            <tr style="background-color: #FF4D4D;">
              <td colspan="12">${tipp.url?:'URL Not specified'}</th>
            </tr>
          </g:if>
          <g:else>
            <tr style="background-color: #4DFF4D;">
              <td rowspan="2"> </td>
              <td> ${tipp.type} </td>
              <td> ${tipp.title.name} </td>
              <td> ${tipp.pkg.name} </td>
              <td> ${tipp.hostPlatform.name} </td>
              <td> <input type="date" name="_tippdata:${tipp.parent}:${tipp.seq}:startDate" value="${tipp.startDate}"/> </td>
              <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:startVolume" value="${tipp.startVolume}" style="width:40px;"/> </td>
              <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:startIssue" value="${tipp.startIssue}" style="width:40px;"/> </td>
              <td> <input type="date" name="_tippdata:${tipp.parent}:${tipp.seq}:endDate" value="${tipp.endDate}"/> </td>
              <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:endVolume" value="${tipp.endVolume}" style="width:40px;"/> </td>
              <td> <input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:endIssue" value="${tipp.endIssue}" style="width:40px;"/> </td>
              <td> <button type="submit" name="remove" value="${tipp.parent}:${tipp.seq}" class="btn btn-warn">Remove</button></td>
              <td> <input name="_tippdata:${tipp.parent}:${tipp.seq}:review" type="checkbox" value="on" ${tipp.review=='on'?'checked':''} /></td>
            </tr>
            <tr style="background-color: #4DFF4D;">
              <td colspan="12"><input type="text" name="_tippdata:${tipp.parent}:${tipp.seq}:url" value="${tipp.url}" /> </td>
            </tr>
          </g:else>
          </g:each>
        </tbody>
      </table>
      <button type="submit" class="btn btn-default btn-success btn-sm pull-right" name="update" value="update">Update</button>
      <div class="panel-footer clearfix">
        <g:if test="${d.status?.value=='Active'}">
          <p>Use the following form to indicate the package and platform
            for new TIPPs. Select/De-select TIPPS above to indicate</p>

          <dl class="dl-horizontal clearfix">
            <dt>New Package</dt>
            <dd>
              <g:simpleReferenceTypedown class="form-control" name="Package"
                baseClass="org.gokb.cred.Package" />
            </dd>
            <dt>New Platform</dt>
            <dd>
              <g:simpleReferenceTypedown class="form-control" name="Platform"
                baseClass="org.gokb.cred.Platform" />
            </dd>
            <dt></dt>
            <dd>
              <button type="submit" class="btn btn-default btn-primary btn-sm"
                name="addTransferTipps" value="AddTipps">Add transfer
                tipps</button>
            </dd>
          </dl>
          <div class="btn-group clearfix pull-right">
            <button type="submit"
              class="btn btn-default btn-success btn-sm pull-right"
              name="process" value="process">Process Transfer</button>
            <button type="submit" class="btn btn-default btn-danger btn-sm "
              name="abandon" value="abandon">Abandon Transfer</button>
          </div>
        </g:if>
        <g:else>
            This activity has been completed.
          </g:else>
      </div>
    </div>
  </g:form>
    
  <!--
    ${d.activityData}
  -->

</body>
</html>

