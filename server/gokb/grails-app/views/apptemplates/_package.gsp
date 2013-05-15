
<h1>Package: <span class="ipe" 
                   data-type="text" 
                   data-pk="${d.class.name}:${d.id}" 
                   data-name="name" 
                   data-url="<g:createLink controller='ajaxSupport' action='edit'/>" 
                   data-original-title="ProjectName">${d.name}</span> </h1>

<table class="table table-bordered table-striped" style="clear: both"><tbody>
  <tr><td>Internal Id</td>            <td>${d.id}</td></tr>
  <tr><td>Package Name</td>           <td>${d.name}</td></tr>
  <tr><td>Shortcode</td>              <td>${d.shortcode}</td></tr>
  <tr><td>Tags</td>                   <td>${d.shortcode}</td></tr>
  <tr><td>Status</td>                 <td><span class="ipe" 
                                                data-pk="${d.class.name}:${d.id}" 
                                                data-type="select" 
                                                data-name="packageStatus"
                                                data-url="<g:createLink controller='ajaxSupport' action='setRef'/>",
                                                data-source="<g:createLink controller='ajaxSupport' action='getRefdata' id='PackageStatus'/>">${d.packageStatus?.value?:'Not Set'}</span></td></tr>
  <tr><td>Breakable</td>              <td>${d.breakable?.value?:'Not Set'}</td></tr>
  <tr><td>Parent</td>                 <td>${d.parent?.value?:'Not Set'}</td></tr>
  <tr><td>Global</td>                 <td>${d.global?.value?:'Not Set'}</td></tr>
  <tr><td>Fixed</td>                  <td>${d.fixed?.value?:'Not Set'}</td></tr>
  <tr><td>Consistent</td>             <td>${d.consistent?.value?:'Not Set'}</td></tr>
  <tr><td>Last Project</td>           <td><g:link controller="resource" action="show" id="${d.lastProject?.class?.name+':'+d.lastProject?.id}">${d.lastProject?.name}</g:link></td></tr>
  <tr><td>Tags</td>                   <td><ul><g:each in="${d.tags}" var="t"><li>${t.value}</li></g:each></ul></td></tr>
  <tr><td>Incoming Combos</td>        <td>
    <ul>
      <g:each in="${d.getOtherIncomingCombos()}" var="c">
        <li><g:link controller="resource" action="show" id="${c.fromComponent.class.name+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
      </g:each>
    </ul></td></tr>
  <tr><td>Outgoing Combos</td>        <td>
    <ul>
      <g:each in="${d.getOtherOutgoingCombos()}" var="c">
        <li>This Org -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.class.name+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
      </g:each>
    </ul></td></tr>
</tbody></table>

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
        <td><g:link controller="resource" action="show" id="${tipp.title.class.name+':'+tipp.title.id}">${tipp.title.name}</g:link>
        <g:link controller="resource" action="show" id="${tipp.class.name+':'+tipp.id}">(tipp)</g:link>
        </td>
        <td><g:link controller="resource" action="show" id="${tipp.hostPlatform.class.name+':'+tipp.hostPlatform.id}">${tipp.hostPlatform.name}</g:link></td>
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

