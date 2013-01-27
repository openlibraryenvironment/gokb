
<h1>Package: <span class="ipe" 
                   data-type="text" 
                   data-pk="${d.class.name}:${d.id}" 
                   data-name="name" 
                   data-url="<g:createLink controller='ajaxSupport' action='edit'/>" 
                   data-original-title="ProjectName">${d.name}</span> </h1>

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
  <dt>Status</dt>
  <dd>${d.packageStatus?.value?:'Not Set'}</dd>
  <dt>Scope</dt>
  <dd>${d.packageScope?.value?:'Not Set'}</dd>
  <dt>Breakable</dt>
  <dd>${d.breakable?.value?:'Not Set'}</dd>
  <dt>Parent</dt>
  <dd>${d.parent?.value?:'Not Set'}</dd>
  <dt>Global</dt>
  <dd>${d.global?.value?:'Not Set'}</dd>
  <dt>Fixed</dt>
  <dd>${d.fixed?.value?:'Not Set'}</dd>
  <dt>Consistent</dt>
  <dd>${d.consistent?.value?:'Not Set'}</dd>
  <dt>Last Project</dt>
  <dd><g:link controller="resource" action="show" id="${d.lastProject?.class?.name+':'+d.lastProject?.id}">${d.lastProject?.name}</g:link></dd>

  <dd>
    <ul>
      <g:each in="${d.tags}" var="t">
        <li>${t.value}</li>
      </g:each>
    </ul>
  </dd>

  <dt>Other Incoming Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.incomingCombos}" var="c">
        <li><g:link controller="resource" action="show" id="${c.fromComponent.class.name+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
      </g:each>
    </ul>
  </dd>
  <dt>Other Outgoing Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.outgoingCombos}" var="c">
        <li>This Org -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.class.name+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
      </g:each>
    </ul>
  </dd>

</dl>

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
        <td><g:link controller="resource" action="show" id="${tipp.title.class.name+':'+tipp.title.id}">${tipp.title.name}</g:link></td>
        <td><g:link controller="resource" action="show" id="${tipp.platform.class.name+':'+tipp.platform.id}">${tipp.platform.name}</g:link></td>
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

<script language="JavaScript">
  $(document).ready(function() {
    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>

