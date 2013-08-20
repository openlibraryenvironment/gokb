<h1>Review Request ${d.id}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Target</dt>
  <dd><g:link controller="resource" action="show" id="${d.componentToReview.getClassName()+':'+d.componentToReview.id}">${d.componentToReview.name?:"Un-named"}</g:link></dd>
  <dt>Cause</dt>
  <dd>${d.descriptionOfCause}</dd>
  <dt>Review Request</dt>
  <dd>${d.reviewRequest}</dd>
  <dt>Request Timestamp</dt>
  <dd>${d.dateCreated}</dd>
  <dt>Request Status</dt>
  <dd><g:xEditableRefData owner="${d}" field="status" config='ReviewRequest.Status' /></dd>
</dl>

