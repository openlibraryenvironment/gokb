<div class="modal-header">
	<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
	<h3 class="modal-title">History ${params.id}</h3>
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
          <td>${hl.oldValue}</td>
          <td>${hl.newValue}</td>
        </tr>
      </g:each>
    </tbody>
  </table>
</div>
<div class="modal-footer">
	<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
</div>
