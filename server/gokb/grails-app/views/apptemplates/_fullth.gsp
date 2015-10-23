
<table class="table table-bordered table-striped">
  <thead>
    <tr>
      <th>On this date</th>
      <th>This/These titles</th>
      <th>Changed to This/These titles</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d?.fullTitleHistory?.fh}" var="theevent" status="i1">
      <tr>
        <td><g:formatDate date="${theevent.eventDate}" format="yyyy-MM-dd"/></td>
        <td>
          <ul>
            <g:each in="${theevent.participants}" var="p">
              <g:if test="${p.participantRole=='in'}">
                <g:if test="${p.participant.id == d.id}"><b></g:if>
                  <li><g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${p.participant.id}">${p.participant.name}</g:link></li>
                <g:if test="${p.participant.id == d.id}"></b></g:if>
              </g:if>
            </g:each>
          </ul>
        </td>
        <td>
          <ul>
            <g:each in="${theevent.participants}" var="p" status="i2">
              <g:if test="${p.participantRole=='out'}">
                <g:if test="${p.participant.id == d.id}"><b></g:if>
                  <li><g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${p.participant.id}">${p.participant.name}</g:link></li>
                <g:if test="${p.participant.id == d.id}"></b></g:if>
              </g:if>
            </g:each>
          </ul>
          <g:each in="${theevent.participants}" var="p" status="i2">
        </td>
        <td>
          <g:link controller="workflow" action="DeleteTitleHistoryEvent" class="confirm-click" data-confirm-message="Are you sure you wish to delete this Title History entry?" id="${theevent.id}">Delete</g:link>
        </td>
      </tr>
    </g:each>
  </tbody>
</table>
