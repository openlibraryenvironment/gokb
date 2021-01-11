<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<asset:stylesheet src="gokb/application.css" />
<asset:javascript src="gokb/application.js" />
<title>GOKb: Title Merge</title>
</head>
<body>
  <g:form controller="workflow" action="editTitleMerge" id="${params.id}">
    <h1 class="page-header">Title Merge</h1>
    <div id="mainarea" class="panel panel-default">
      <div class="panel-heading">
        <h3 class="panel-title">Step 2 of 2</h3>
      </div>
      <div class="panel-body">
        <h3>
          ${d.activityName} - ${d.status?.value}
        </h3>
        <div>Icon indicators: 
          <div><span class="fas fa-check"></span> Item is already connected to the new title</div>
          <div><span class="fas fa-plus" style="color:green;"></span> Item will be added to the new title, if the item type is <b>included</b></div>
        </div>
          <div class="title-merge-info">
            Please note: Once processed, the status of the replaced titles and their TIPPs will be set to <b>Deleted</b> and all of their open ReviewRequests will be closed!
          </div>
        <table class="table table-bordered no-select-all">
          <thead>
            <tr>
              <th rowspan="3" style="vertical-align:top;text-align:center;">Title-ID</th>
              <th colspan="4" class="title-merge-title">Title</th>
            </tr>
            <tr class="title-merge-heading-sub">
              <th>Identifiers <span class="pull-right">(Include <input type="checkbox" name="merge_ids" checked="checked" />)</span></th>
              <th>Alternate Names <span class="pull-right">(Include <input type="checkbox" name="merge_vn" checked="checked" />)</span></th>
              <th>Publishers <span class="pull-right">(Include <input type="checkbox" name="merge_pubs" checked="checked" />)</span></th>
              <th>History <span class="pull-right">(Include <input type="checkbox" name="merge_he" checked="checked" />)</span></th>
            </tr>
            <tr class="title-merge-heading-sub">
              <th colspan="4">TIPPs (Include <input type="checkbox" name="merge_tipps" checked="checked" />)</th>
            </tr>
          </thead>
          <tbody class="title-merge-body-old">
            <g:each in="${oldTitles}" var="ot">
              <tr class="title-merge-heading-old">
                <td rowspan="3" style="text-align:center"> ${ot.id} </td>
                <td colspan="4" class="title-merge-title"> ${ot.name} </td>
              </tr>
              <tr>
                <td> 
                  <g:each in="${ot.ids}" var="ti_id">
                    <div class="title-merge-ids">
                      <span class="label label-primary">${ti_id.namespace.value}</span> 
                      <span class="label label-default">${ti_id.value}</span>
                      <span class="pull-right">
                        <g:if test="${ti_id in newTitle.ids}">
                          <span class="fas fa-check"></span>
                        </g:if>
                        <g:else>
                          <span class="fas fa-plus" style="color:green;"></span>
                        </g:else>
                      </span>
                    </div>
                  </g:each>
                </td>
                <td>
                  <ul>
                    <g:each in="${ot.variantNames}" var="ti_vn">
                      <li>
                        <span>${ti_vn.variantName}</span>
                        <span class="pull-right">
                          <g:if test="${ti_vn in newTitle.variantNames}">
                            <span class="fas fa-check"></span>
                          </g:if>
                          <g:else>
                            <span class="fas fa-plus" style="color:green;"></span>
                          </g:else>
                        </span>
                      </li>
                    </g:each>
                  </ul>
                </td>
                <td>
                  <ul>
                    <g:each in="${ot.publisher}" var="ti_pb">
                      <li>
                        <span>${ti_pb.name}<span>
                        <span class="pull-right">
                          <g:if test="${ti_pb in newTitle.publisher}">
                            <span class="fas fa-check"></span>
                          </g:if>
                          <g:else>
                            <span class="fas fa-plus" style="color:green;"></span>
                          </g:else>
                        </span>
                      </li>
                    </g:each>
                  </ul>
                </td>
                <td>
                  <g:set var="ti_hist" value="${ot.titleHistory}" />
                  <table class="table table-bordered no-select-all">
                    <g:each in="${ti_hist}" var="he">
                      <tr>
                        <td><span>${he.date}</span></td>
                        <td>
                          <g:each in="${he.from}" var="fpart">
                            <div <g:if test="${fpart.id == ot.id}">class="title-merge-heading"</g:if>>${fpart.name}</div>
                          </g:each>
                        </td>
                        <td>
                          <g:each in="${he.to}" var="tpart">
                            <div <g:if test="${tpart.id == ot.id}">class="title-merge-heading"</g:if>>${tpart.name}</div>
                          </g:each>
                        </td>
                      </tr>
                    </g:each>
                  </table>
                </td>
              </tr>
              <tr>
                <td colspan="4">
                    <table class="table table-bordered no-select-all">
                      <thead>
                        <tr>
                          <th rowspan="2" style="vertical-align:top;text-align:center">
                            <div>TIPP-ID</div>
                            <div style="margin-top:1.4em;">Status</div>
                          </th>
                          <th>Package</th>
                          <th>Platform</th>
                          <th>Start Date</th>
                          <th>Start Volume</th>
                          <th>Start Issue</th>
                          <th>End Date</th>
                          <th>End Volume</th>
                          <th>End Issue</th>
                        </tr>
                        <tr>
                          <th colspan="8">URL</th>
                        </tr>
                      </thead>
                      <tbody class="title-merge-body-old">
                        <g:each in="${ot.tipps}" var="tipp">
                          <tr>
                            <td rowspan="2" style="text-align:center;" class="title-merge-heading-old">
                              <div>${tipp.id}</div>
                              <div class="title-merge-status">
                                <g:if test="${tipp.status?.value == 'Current'}">
                                  <span style="color:green;">${tipp.status.value}</span>
                                </g:if>
                                <g:else>
                                  <span>${tipp.status?.value ?: 'Status Unknown'}</span>
                                </g:else>
                              </div>
                            </td>
                            <td> ${tipp.pkg.name} </td>
                            <td> ${tipp.hostPlatform.name} </td>
                            <td> ${tipp.startDate} </td>
                            <td> ${tipp.startVolume} </td>
                            <td> ${tipp.startIssue} </td>
                            <td> ${tipp.endDate} </td>
                            <td> ${tipp.endVolume} </td>
                            <td> ${tipp.endIssue} </td>
                          </tr>
                          <tr>
                            <td colspan="8"> ${tipp.url ?: 'TIPP URL not present'} </td>
                          </tr>
                        </g:each>
                      </tbody>
                    </table>
                </td>
              </tr>
            </g:each>
            <tr class="title-merge-space">
              <td colspan="5"><span class="fas fa-arrow-down"></span></td>
            </tr>
            <tr class="title-merge-heading-new">
              <td rowspan="3" style="text-align:center;"> ${newTitle.id} </td>
              <td colspan="4" class="title-merge-title"> ${newTitle.name} </td>
            </tr>
            <tr class="title-merge-body-new">
              <td> 
                <g:each in="${newTitle.ids}" var="ti_id">
                  <div class="title-merge-ids">
                    <span class="label label-primary">${ti_id.namespace.value}</span> 
                    <span class="label label-default">${ti_id.value}</span>
                  </div>
                </g:each>
              </td>
              <td>
                <g:each in="${newTitle.variantNames}" var="ti_vn">
                  <div><span>${ti_vn.variantName}</span></div>
                </g:each>
              </td>
              <td>
                <g:each in="${newTitle.publisher}" var="ti_pb">
                  <div><span>${ti_pb.name}</span></div>
                </g:each>
              </td>
              <td>
                <g:set var="ti_hist" value="${newTitle.titleHistory}" />
                <table class="table table-bordered no-select-all">
                  <g:each in="${ti_hist}" var="he">
                    <tr>
                      <td><span>${he.date}</span></td>
                      <td>
                        <g:each in="${he.from}" var="fpart">
                          <div <g:if test="${fpart.id == newTitle.id}">class="title-merge-heading"</g:if>>${fpart.name}</div>
                        </g:each>
                      </td>
                      <td>
                        <g:each in="${he.to}" var="tpart">
                          <div <g:if test="${tpart.id == newTitle.id}">class="title-merge-heading"</g:if>>${tpart.name}</div>
                        </g:each>
                      </td>
                    </tr>
                  </g:each>
                </table>
              </td>
            </tr>
            <tr class="title-merge-body-new">
              <td colspan="4">
                  <table class="table table-bordered no-select-all">
                    <thead>
                      <tr>
                        <th rowspan="2" style="vertical-align:top;text-align:center;">
                            <div>TIPP-ID</div>
                            <div style="margin-top:1.4em;">Status</div>
                        </th>
                        <th>Package</th>
                        <th>Platform</th>
                        <th>Start Date</th>
                        <th>Start Volume</th>
                        <th>Start Issue</th>
                        <th>End Date</th>
                        <th>End Volume</th>
                        <th>End Issue</th>
                      </tr>
                      <tr>
                        <th colspan="8">URL</th>
                      </tr>
                    </thead>
                    <tbody class="title-merge-body-new">
                      <g:each in="${newTitle.tipps}" var="tipp">
                          <tr>
                            <td rowspan="2" style="text-align:center;" class="title-merge-heading-new">
                              <div>${tipp.id}</div>
                              <div class="title-merge-status">
                                <g:if test="${tipp.status?.value == 'Current'}">
                                  <span style="color:green;">${tipp.status.value}</span>
                                </g:if>
                                <g:else>
                                  <span>${tipp.status?.value ?: 'Status Unknown'}</span>
                                </g:else>
                              </div>
                            </td>
                            
                            <td> ${tipp.pkg.name} </td>
                            <td> ${tipp.hostPlatform.name} </td>
                            <td> ${tipp.startDate} </td>
                            <td> ${tipp.startVolume} </td>
                            <td> ${tipp.startIssue} </td>
                            <td> ${tipp.endDate} </td>
                            <td> ${tipp.endVolume} </td>
                            <td> ${tipp.endIssue} </td>
                          </tr>
                          <tr>
                            <td colspan="8"> ${tipp.url ?: 'TIPP URL not present'} </td>
                          </tr>
                      </g:each>
                    </tbody>
                  </table>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="btn-group clearfix pull-right">
        <button type="submit" class="btn btn-default btn-success btn-sm pull-right" name="update" value="update">Update</button>
        <button type="submit" class="btn btn-default btn-success btn-sm pull-right" name="process" value="process">Process Merge</button>
        <button type="submit" class="btn btn-default btn-danger btn-sm " name="abandon" value="abandon">Abandon Merge</button>
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

