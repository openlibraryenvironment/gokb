<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#rrdets" data-toggle="tab">Review
        Request Details</a></li>
  </ul>
    <nav class="navbar navbar-inverse">
    <div class="container-fluid">
      <ul class="nav navbar-nav navbar-right">
        <li><a data-toggle="modal" data-cache="false"
          title="Show History"
          data-remote='<g:createLink controller="fwk" action="history" id="${d.class.name}:${d.id}"/>'
          data-target="#modal"><i class="glyphicon glyphicon-time"></i></a></li>

        <li><a data-toggle="modal" data-cache="false"
          title="Show Notes"
          data-remote='<g:createLink controller="fwk" action="notes" id="${d.class.name}:${d.id}"/>'
          data-target="#modal"><i class="glyphicon glyphicon-comment"></i></a></li>

      </ul>
    </div>
    </nav>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="rrdets">
      <dl class="dl-horizontal">
        <dt>
          <g:annotatedLabel owner="${d}" property="id">Internal ID</g:annotatedLabel>
        </dt>
        <dd>
          ${d.id?:'New record'}&nbsp;
        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="cause">Cause</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable class="ipe" owner="${d}" field="descriptionOfCause" />
        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="reviewRequest">Review Request</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditable class="ipe" owner="${d}" field="reviewRequest" />
        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="status">Request Status</g:annotatedLabel>
        </dt>
        <dd>
          <g:xEditableRefData owner="${d}" field="status"
            config='ReviewRequest.Status' />
        </dd>

        <dt>
          <g:annotatedLabel owner="${d}" property="target">Component</g:annotatedLabel>
        </dt>

        <dd>
          <g:manyToOneReferenceTypedown owner="${d}"
            field="componentToReview" baseClass="org.gokb.cred.KBComponent">
            ${d.componentToReview?.displayName?:''}
          </g:manyToOneReferenceTypedown>
          <g:componentLink object="${d?.componentToReview}" target="_blank"
            title="View the component (opens in new window)">view</g:componentLink>
        </dd>
        <dt>
          <g:annotatedLabel owner="${d}" property="refineProject">Refine Project</g:annotatedLabel>
        </dt>
        <dd>
          <g:if test="${d.refineProject != null}">
            <g:link controller="resource" action="show"
              id="${d.refineProject.class.name}:${d.refineProject.id}">
              ${d.refineProject.name}
            </g:link>
          </g:if>
          <g:else>N/A</g:else>
        </dd>

        <g:if test="${d.id != null}">
          <dt>
            <g:annotatedLabel owner="${d}" property="dateCreated">Request Timestamp</g:annotatedLabel>
          </dt>
          <dd>
            ${d.dateCreated}&nbsp;
          </dd>
        </g:if>
        <g:else>
          Additional fields will be available once the record is saved
        </g:else>

        <g:if test="${d.additional}">
       
          <table class="table table-striped">
            <thead>
              <tr>
                <th>Specific Problems</th>
                <th>Possible Resolutions</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${d.additional?.problems}" var="revreq_problem"> 
                <tr>
                 <td>
                   <h2>${revreq_problem.problemCode}</h2>
                   <p>${revreq_problem.problemDescription}</p>
                 </td>
                 <td>
                   <g:render template="${revreq_problem.problemCode}" contextPath="../reviewRequestCases"  model="${[d:d]}" />
                 </td>
                </tr>
              </g:each>
            </tbody>
          </table>
        </g:if>
      </dl>
    </div>
  </div>

  <div id="modal" class="qmodal modal fade" role="dialog">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
          <h3 class="modal-title">Modal header</h3>
        </div>
        <div class="modal-body"></div>
        <div class="modal-footer">
          <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
        </div>
      </div>
    </div>
  </div>
</div>
