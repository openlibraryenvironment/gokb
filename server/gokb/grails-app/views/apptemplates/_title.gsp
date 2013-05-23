<h1>Title: ${d.name}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Title</dt>
  <dd>${d.name}</dd>
  <dt>Identifiers</dt>
  <dd>
    <ul>
      <g:each in="${d.ids}" var="id">
        <li>${id.namespace.value}:${id.value}</li>
      </g:each>
    </ul>
  </dd>
  <dt>Tags</dt>
  <dd>
    <ul>
      <g:each in="${d.tags}" var="t">
        <li>${t.value}</li>
      </g:each>
    </ul>
  </dd>
  <dt>Package Appearences</dt>
  <dd>
    <table class="table table-striped">
      <thead>
        <tr>
          <th>Package</th>
          <th>Platform</th>
          <th>Start Date</th>
          <th>Start Volume</th>
          <th>Start Issue</th>
          <th>End Date</th>
          <th>End Volume</th>
          <th>End Issue</th>
          <th>Embargo</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.tipps}" var="tipp">
          <tr>
            <td><g:link controller="resource" action="show" id="${tipp.pkg.getClassName()+':'+tipp.pkg.id}">${tipp.pkg.name}</g:link></td>
            <td><g:link controller="resource" action="show" id="${tipp.hostPlatform.getClassName()+':'+tipp.hostPlatform.id}">${tipp.hostPlatform.name}</g:link></td>
            <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${tipp.startDate}"/></td>
            <td>${tipp.startVolume}</td>
            <td>${tipp.startIssue}</td>
            <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${tipp.endDate}"/></td>
            <td>${tipp.endVolume}</td>
            <td>${tipp.endIssue}</td>
            <td>${tipp.embargo}</td>
          </tr>
        </g:each>
      </tbody>
    </table>
  </dd>
  <dt>Other Incoming Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.getOtherIncomingCombos()}" var="c">
        <li><g:link controller="resource" action="show" id="${c.fromComponent.getClassName()+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
      </g:each>
    </ul>
  </dd>
  <dt>Other Outgoing Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.getOtherOutgoingCombos()}" var="c">
        <li>This Title -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.getClassName()+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
      </g:each>
    </ul>
  </dd>
</dl>

