
<table class="table table-bordered table-striped show-more" data-show-more-rows='2' >
  <thead>
    <tr>
      <th>On this date</th>
      <th>This/These titles</th>
      <th>Changed to This/These titles</th>
      <g:if test="${d.isEditable()}">
        <th>Actions</th>
      </g:if>
    </tr>
  </thead>
  <tbody>
    <g:set var="fth" value="${d?.fullTitleHistory?.fh}"/>

    <g:each in="${fth}" var="theevent" status="i1">
      <tr>
        <td>
          <g:xEditable class="ipe" owner="${theevent}" type="date" field="eventDate" />
        </td>
        <td>
          <ul>
            <g:each in="${theevent?.participants}" var="p">
              <g:if test="${p.participantRole=='in'}">
                <g:if test="${p.participant?.id == d?.id}"><b></g:if>
                  <li><g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${p.participant?.id}">${p.participant?.name}</g:link></li>
                <g:if test="${p.participant?.id == d?.id}"></b></g:if>
              </g:if>
            </g:each>
          </ul>
        </td>
        <td>
          <ul>
            <g:each in="${theevent?.participants}" var="p" status="i2">
              <g:if test="${p.participantRole=='out'}">
                <g:if test="${p.participant?.id == d?.id}"><b></g:if>
                  <li><g:link controller="resource" action="show" id="org.gokb.cred.TitleInstance:${p.participant?.id}">${p.participant?.name}</g:link></li>
                <g:if test="${p.participant?.id == d?.id}"></b></g:if>
              </g:if>
            </g:each>
          </ul>
        </td>
        <g:if test="${d.isEditable()}">
          <td>
              <g:link controller="workflow" action="DeleteTitleHistoryEvent" class="confirm-click" data-confirm-message="Are you sure you wish to delete this Title History entry?" id="${theevent?.id}">Delete</g:link>
          </td>
        </g:if>
      </tr>
    </g:each>
  </tbody>
</table>
