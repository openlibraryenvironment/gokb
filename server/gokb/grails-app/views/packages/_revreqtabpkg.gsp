<h4>Showing <b>${d.restriction}</b> Requests (${d.reviewRequests?.size() ?: '0'})</h4>

<table class="table table-bordered">
  <thead>
    <tr>
      <th>Cause</th>
      <th>Request</th>
      <th>Component</th>
      <th>Timestamp</th>
      <th>Status</th>
      <th>Raised By</th>
      <th>Reviewed By</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d.reviewRequests}" var="rr">
      <tr>
        <td>
          <g:link controller="resource" action="show" id="org.gokb.cred.ReviewRequest:${rr.id}">${rr.descriptionOfCause}</g:link>
        </td>
        <td>
          <g:link controller="resource" action="show" id="org.gokb.cred.ReviewRequest:${rr.id}">${rr.reviewRequest}</g:link>
        </td>
        <td>
          <g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${rr.componentToReview.id}">${rr.componentToReview?.name ?: rr.componentToReview}</g:link>
        </td>
        <td>
          ${rr.dateCreated}
        </td>
        <td>
          <g:xEditableRefData owner="${rr}" field="status" config='ReviewRequest.Status' />
        </td>
        <td>
          ${rr.raisedBy?.displayName?:rr.raisedBy?.username}
        </td>
        <td>
          ${rr.reviewedBy?.displayName?:rr.reviewedBy?.username}
        </td>
      </tr>
    </g:each>
  </tbody>
</table>
