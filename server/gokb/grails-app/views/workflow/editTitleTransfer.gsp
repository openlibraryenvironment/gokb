<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <r:require modules="editable"/>
    <title>GOKb::Title Transfer</title>
  </head>
  <body>
    <div class="container-fluid">
      <g:form controller="workflow" action="editTitleTransfer" id="${params.id}">
        <div class="row-fluid">
          <div class="span12 hero well">
            ${d.activityName} - ${d.status?.value}
          </div>
        </div>
        <div class="row-fluid">
  
          <div class="span12">

            The following titles:
            <ul>
              <g:each in="${titles}" var="title">
                <li>${title.name}</li>
              </g:each>
            </ul>

            Will be transferred from their current publisher to ${newPublisher.name}. <span style="background-color:#FF4D4D;">Current tipps shown with a red background</span> will be deprecated. <span style="background-color:#11bb11;">New tipps with a green background</span> will be created by this transfer.

            <table class="table">
              <thead>
                <tr>
                  <th>Select</th>
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
                  <th>Close</th>
                  <th>Review</th>
                </tr>
              </thead>
              <tbody>
                <g:each in="${tipps}" var="tipp">
                  <tr style="background-color: ${tipp.type=='NEW'?'#4DFF4D':'#FF4D4D'};">
                    <td><g:if test="${tipp.type=='CURRENT'}"><input name="addto-${tipp.id}" type="checkbox" checked="true"/></g:if></td>
                    <td>${tipp.type}</td>
                    <td>${tipp.title.name}</td>
                    <td>${tipp.pkg.name}</td>
                    <td>${tipp.hostPlatform.name}</td>
                    <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${tipp.startDate}"/></td>
                    <td>${tipp.startVolume}</td>
                    <td>${tipp.startIssue}</td>
                    <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${tipp.endDate}"/></td>
                    <td>${tipp.endVolume}</td>
                    <td>${tipp.endIssue}</td>
                    <td><g:if test="${tipp.type=='CURRENT'}"><input name="close-${tipp.id}" type="checkbox" checked="true"/></g:if></td>
                    <td><input name="review-${tipp.id}" type="checkbox" checked="true"/></td>
                  </tr>
                </g:each>
              </tbody>
            </table>

            <g:if test="${d.status?.value=='Active'}">
              Use the following form to indicate the package and platform for new TIPPs. Select/Deselect TIPPS above to indicate

              <dl class="dl-horizontal">
                <div class="control-group">
                  <dt>New Package</dt>
                  <dd><g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="Package" baseClass="org.gokb.cred.Package"/></dd>
                </div>
  
                <div class="control-group">
                  <dt>New Platform</dt>
                  <dd><g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="Platform" baseClass="org.gokb.cred.Platform"/></dd>
                </div>
                <div class="control-group">
                  <dt></dt>
                  <dd><button type="submit" class="btn btn-primary" name="addTransferTipps" value="AddTipps">Add transfer tipps</button></dd>
                </div>
              </dl>
 
              <br/>

              <button type="submit" class="btn btn-primary" name="process" value="process">Process Transfer</button>
              <button type="submit" class="btn btn-danger" name="abandon" value="abandon">Abandon Transfer</button>
            </g:if>
            <g:else>
              This activity has been completed.
            </g:else>
          </div>

 
        </div>
      </g:form>
    </div>
  </body>
</html>

