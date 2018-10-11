<div class="modal-header">
	<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
	<h3 class="modal-title">Recent History for <i><b>${params.id}</b></i></h3>
</div>
<div class="modal-body">
    <table class="table table-striped table-bordered">
    <thead>
      <tr>
        <th>Event ID</th>
        <th>Agent</th>
        <th>Date</th>
        <th>Event</th>
        <th>Field</th>
        <th>Old Value</th>
        <th>New Value</th>
      </tr>
    </thead>
    <tbody>
      <g:each in="${historyLines}" var="hl">
        <tr>
          <td>${hl.id}</td>
          <td style="white-space:nowrap;">${hl.actor}</td>
          <td style="white-space:nowrap;">${hl.dateCreated}</td>
          <td style="white-space:nowrap;">${hl.eventName}</td>
          <td style="white-space:nowrap;">${hl.propertyName}</td>
          <td>
            <g:each in="${hl.oldValue}" var="ov">
              <g:if test="${ov.oid}">
                <div><g:link controller="resource" action="show" id="${ov.oid}">${ov.val ?: ov.oid}</g:link></div>
              </g:if>
              <g:else>
                ${ov.val}
              </g:else>
            </g:each>
          </td>
          <td>
            <g:each in="${hl.newValue}" var="nv">
              <g:if test="${nv.oid}">
                <div><g:link controller="resource" action="show" id="${nv.oid}">${nv.val ?: nv.oid}</g:link></div>
              </g:if>
              <g:else>
                ${nv.val}
              </g:else>
            </g:each>
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
</div>
<div class="modal-footer">
	<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
</div>
