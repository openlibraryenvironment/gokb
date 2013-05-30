
<h1>Package: <span class="ipe" 
                   data-type="text" 
                   data-pk="${d.getClassName()}:${d.id}" 
                   data-name="name" 
                   data-url="<g:createLink controller='ajaxSupport' action='edit'/>" 
                   data-original-title="ProjectName">${d.name}</span> </h1>

<table class="table table-bordered table-striped" style="clear: both"><tbody>
  <tr><td>Internal Id</td>            <td>${d.id}</td></tr>
  <g:if test="${ d.ids?.size() > 0 }" >
	  <tr>
	  	<td>Identifiers</td>
	  	<td><ul>
	      <g:each in="${d.ids}" var="id" >
	        <li>${id.namespace.value}:${id.value}</li>
	      </g:each>
	    </ul></td>
	  </tr>
  </g:if>
  <tr><td>Package Name</td>           <td>${d.name}</td></tr>
  <tr><td>Shortcode</td>              <td>${d.shortcode}</td></tr>
  <tr><td>Provider</td>              <td>${d.provider.name}</td></tr>
  <tr><td>Status</td>                 <td><span class="ipe" 
                                                data-pk="${d.getClassName()}:${d.id}" 
                                                data-type="select" 
                                                data-name="packageStatus"
                                                data-url="<g:createLink controller='ajaxSupport' action='setRef'/>",
                                                data-source="<g:createLink controller='ajaxSupport' action='getRefdata' id='KBComponent.Status'/>">${d.status?.value?:'Not Set'}</span></td></tr>
  <tr><td>Breakable</td>              <td>${d.breakable?.value?:'Not Set'}</td></tr>
  <tr><td>Global</td>                 <td>${d.global?.value?:'Not Set'}</td></tr>
  <tr><td>Fixed</td>                  <td>${d.fixed?.value?:'Not Set'}</td></tr>
  <tr><td>Consistent</td>             <td>${d.consistent?.value?:'Not Set'}</td></tr>
  <tr><td>Last Project</td>           <td><g:link controller="resource" action="show" id="${d.lastProject?.class.name+':'+d.lastProject?.id}">${d.lastProject?.name}</g:link></td></tr>
  <tr><td>Tags</td>                   <td><ul><g:each in="${d.tags}" var="t"><li>${t.value}</li></g:each></ul></td></tr>
  <tr><td>Incoming Combos</td>        <td>
    <ul>
      <g:each in="${d.getOtherIncomingCombos()}" var="c">
        <li><g:link controller="resource" action="show" id="${c.fromComponent.getClassName()+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
      </g:each>
    </ul></td></tr>
  <tr><td>Outgoing Combos</td>        <td>
    <ul>
      <g:each in="${d.getOtherOutgoingCombos()}" var="c">
        <li>This Org -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.getClassName()+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
      </g:each>
    </ul></td></tr>
</tbody></table>
<table class="table table-bordered table-striped" style="clear: both"><tbody>
  <tr><td><g:link controller="search" action="index" params="[qbe:'g:tipps', qp_pkg_id:d.id]" id="">Titles in this package</g:link></td></tr>
</tbody></table>
<script language="JavaScript">
  $(document).ready(function() {
    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>

