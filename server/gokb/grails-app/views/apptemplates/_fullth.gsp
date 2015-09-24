
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
          <g:each in="${theevent.participants}" var="p">
            <g:if test="${p.participantRole=='in'}">
              <g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${p.participant.id}">${p.participant.name}</g:link> &nbsp;
            </g:if>
          </g:each>
        </td>
        <td>
          <g:each in="${theevent.participants}" var="p" status="i2">
            <g:if test="${p.participantRole=='out'}">
              <g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${p.participant.id}">${p.participant.name}</g:link> &nbsp;
            </g:if>
          </g:each>
        </td>

      </tr>
    </g:each>
  </tbody>
</table>
