
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
</dl>

<h2>Titles in this package</h2>

<table class="table table-striped">
  <thead>
    <tr>
      <th>Info</th>
      <th>More info</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d.tipps}" var="tipp">
      <tr>
        <td>${tipp}</td>
      </tr>
    </g:each>
  </tbody>
</table>
