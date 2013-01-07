
<h1>Package: ${d.name}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Package Identifier</dt>
  <dd>${d.identifier}</dd>
  <dt>Package Name</dt>
  <dd>${d.name}</dd>
  <dt>Shortcode</dt>
  <dd>${d.shortcode}</dd>
  <dt>Tags</dt>
  <dd>
    <ul>
      <g:each in="${d.tags}" var="t">
        <li>${t.value}</li>
      </g:each>
    </ul>
  </dd>
</dl>

<h2>Titles in this package</h2>

<table class="table table-striped">
  <thead>
    <tr>
      <th>Title</th>
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
        <td>${tipp.title.name}</td>
        <td><g:formatDate format="dd MMMM yyyy" date="${tipp.startDate}"/></td>
        <td>${tipp.startVolume}</td>
        <td>${tipp.startIssue}</td>
        <td><g:formatDate format="dd MMMM yyyy" date="${tipp.endDate}"/></td>
        <td>${tipp.endVolume}</td>
        <td>${tipp.endIssue}</td>
        <td>${tipp.embargo}</td>
      </tr>
    </g:each>
  </tbody>
</table>
