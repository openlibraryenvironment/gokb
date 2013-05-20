<h2>Titles in this package</h2>

<table class="table table-striped">
  <thead>
    <tr>
      <th>Title</th>
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
        <td><g:link controller="resource" action="show" id="${tipp.title.getClassName()+':'+tipp.title.id}">${tipp.title.name}</g:link>
        <g:link controller="resource" action="show" id="${tipp.getClassName()+':'+tipp.id}">(tipp)</g:link>
        </td>
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