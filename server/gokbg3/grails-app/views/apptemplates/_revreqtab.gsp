<div>
  <table class="table table-bordered">
    <thead>
      <tr>
        <th>Cause</th>
        <th>Request</th>
        <th>Status</th>
        <th>Date Created</th>
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
            <g:xEditableRefData owner="${rr}" field="status" config='ReviewRequest.Status' />
          </td>
          <td>
            ${rr.dateCreated}
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
</div>
<div>
  <button
  class="hidden-license-details btn btn-default btn-primary "
  data-toggle="collapse" data-target="#collapseableAddReview">
  Add new <i class="fas fa-plus"></i></button>
  <dl id="collapseableAddReview" class="dl-horizontal collapse">
    <g:form controller="workflow" action="newRRLink" class="form-inline">
      <input type="hidden" name="id" value="${d.id}" />
      <dt class="dt-label">Aspect to review</dt>
      <dd>
        <input class="form-control" type="text" name="request" required />
      </dd>
      <dt class="dt-label"></dt>
      <dd>
        <button type="submit" class="btn btn-default btn-primary">Add</button>
      </dd>
    </g:form>
  </dl>
</div>
<div style="clear:both"></div>
