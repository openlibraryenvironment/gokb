<table class="table table-bordered">
  <thead>
    <tr><th>Cause</th><th>Request</th><th>Status</th><th>Raised By</th><th>Reviewed By</th></tr>
  </thead>
  <tbody>
    <g:each in="${d.reviewRequests}" var="rr">
      <tr>
        <td>${rr.cause.propertyName}</td>
        <td>${rr.request}</td>
        <td>${rr.status?.value}</td>
        <td>${rr.raisedBy?.displayName</td>
        <td>${rr.reviewedBy?.displayName}</td>
        <td></td>
      </tr>
    </g:each>
    </tr>
  </tbody>
</table>



