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
          <td style="white-space:nowrap;">
            <g:if test="${hl.className == 'Combo'}">
              <g:set var="cobj" value="${org.gokb.cred.Combo.get(Long.valueOf(hl.persistedObjectId.split(':')[1]))}" />
              ${cobj?.type?.value}
            </g:if>
            <g:else>
              ${hl.propertyName}
            </g:else>
          </td>
          <td>
            <g:if test="${hl.oldValue?.startsWith('[id:org')}">
              <g:set var="valuePart" value="${hl.oldValue.split(']')[1]}" />
              <g:set var="oidPart" value ="${hl.oldValue.split(']')[0].substring(4)}" />
              <g:link controller="resource" action="show" id="${oidPart}">${valuePart ?: oidPart}</g:link>
            </g:if>
            <g:elseif test="${hl.oldValue?.startsWith('[id:')}">
              ${hl.oldValue.split(']')[1]}
            </g:elseif>
            <g:else>
              ${hl.oldValue}
            </g:else>
          </td>
          <td>
            <g:if test="${hl.newValue?.startsWith('[id:org')}">
              <g:set var="valuePart" value="${hl.newValue.split(']')[1]}" />
              <g:set var="oidPart" value ="${hl.newValue.split(']')[0].substring(4)}" />
              <g:link controller="resource" action="show" id="${oidPart}">${valuePart ?: oidPart}</g:link>
            </g:if>
            <g:elseif test="${hl.newValue?.startsWith('[id:')}">
              ${hl.newValue.split(']')[1]}
            </g:elseif>
            <g:else>
              ${hl.newValue}
            </g:else>
          </td>
        </tr>
      </g:each>
    </tbody>
  </table>
</div>
<div class="modal-footer">
	<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
</div>
