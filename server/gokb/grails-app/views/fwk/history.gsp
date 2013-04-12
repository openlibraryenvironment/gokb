<h4>History ${params.id}</h4>
<p>
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
</p>
