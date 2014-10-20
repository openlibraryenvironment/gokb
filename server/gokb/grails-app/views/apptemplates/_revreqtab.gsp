<table class="table table-bordered">
  <thead>
    <tr>
      <th>Cause</th>
      <th>Request</th>
      <th>Status</th>
      <th>Raised By</th>
      <th>Reviewed By</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d.reviewRequests}" var="rr">
      <tr>
        <td>
          ${rr.descriptionOfCause}
        </td>
        <td>
          ${rr.reviewRequest}
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
