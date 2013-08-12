
<h1>Package: ${d.name}</h1>


<g:if test="${flash?.message}">
  <bootstrap:alert class="alert-info">${flash.message}</bootstrap:alert>
</g:if>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Package Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name">${d.name}</g:xEditable></dd>
  </div>

  <div class="control-group">
    <dt>Internal Id</dt>
    <dd>${d.id?:'New Record'}</dd>
  </div>

    <g:if test="${ d.ids?.size() > 0 }" >
      <div class="control-group">
      <dt>Identifiers</dt>
      <dd><ul>
	      <g:each in="${d.ids}" var="id" >
	        <li>${id.namespace.value}:${id.value}</li>
	      </g:each>
	    </ul></dd>
      </div>
    </g:if>
    <div class="control-group">
      <dt>Shortcode</dt>
      <dd><g:xEditable class="ipe" owner="${d}" field="shortcode">${d.shortcode}</g:xEditable></dd>
    </div>
    <div class="control-group">
    <dt>Provider</dt>
    <dd>${d.provider?.name?:'Provider Not Set'}</dd>
    </div>
    <div class="control-group">
    <dt>Status</dt>
    <dd><span class="ipe"
              data-pk="${__oid}" 
              data-type="select" 
              data-name="status"
              data-url="<g:createLink controller='ajaxSupport' action='setRef'/>",
              data-source="<g:createLink controller='ajaxSupport' action='getRefdata' id='KBComponent.Status'/>">${d.status?.value?:'Not Set'}</span></dd>
    </div>
    <div class="control-group">
    <dt>Breakable</dt>
    <dd>${d.breakable?.value?:'Breakable Not Set'}</dd>
    </div>
    <div class="control-group">

    <dt>Global</dt>
    <dd>${d.global?.value?:'GLobal Not Set'}</dd>

    </div>
    <div class="control-group">
    <dt>Fixed</dt>
    <dd>${d.fixed?.value?:'Fixed Not Set'}</dd>

    </div>
    <div class="control-group">
    <dt>Consistent</dt>
    <dd>${d.consistent?.value?:'Consistent Not Set'}</dd>
    </div>

<g:if test="${d.id != null}">
    <div class="control-group">
    <dt>Incoming Combos</dt>
    <dd>&nbsp;
      <ul>
        <g:each in="${d.getOtherIncomingCombos()}" var="c">
          <li><g:link controller="resource" action="show" id="${c.fromComponent.getClassName()+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
        </g:each>
      </ul>
    </dd>
    </div>
    <div class="control-group">
    <dt>Outgoing Combos</dt>
    <dd>
      <ul>&nbsp;
        <g:each in="${d.getOtherOutgoingCombos()}" var="c">
          <li>This Org -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.getClassName()+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
        </g:each>
      </ul>
    </dd>
    </div>
    <div class="control-group">

    <dt>Last Project</dt>
    <dd><g:link controller="resource" action="show" id="${d.lastProject?.getClassName()+':'+d.lastProject?.id}">${d.lastProject?.name}</g:link></dd>
    </div>

    <div class="control-group">
    <dt>Tags</dt>
    <dd>&nbsp;
      <ul><g:each in="${d.tags}" var="t"><li>${t.value}</li></g:each></ul>
    </dd>
    </div>
    
    <table class="table table-bordered table-striped" style="clear: both"><tbody>
      <tr><td><g:link controller="search" action="index" params="[qbe:'g:tipps', qp_pkg_id:d.id]" id="">Titles in this package</g:link></td></tr>
    </tbody></table>
</g:if>
<g:else>
  <p>Other properties will be editable once the package has been saved</p>
</g:else>

<script language="JavaScript">
  $(document).ready(function() {
    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>

