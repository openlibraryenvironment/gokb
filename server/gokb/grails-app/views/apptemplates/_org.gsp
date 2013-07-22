<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Organisation: ${d.name}</h1>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Internal ID</dt>
    <dd>${d.id}</dd>
  </div>

  <div class="control-group">
    <dt>Org Name</dt>
    <dd><g:xEditable class="ipe" owner="${d}" field="name">${d.name}</g:xEditable></dd>
  </div>

  <div class="control-group">
    <dt>Status</dt><dd><span class="ipe" 
         data-pk="${d.getClassName()}:${d.id}" 
         data-type="select" 
         data-name="packageStatus"
         data-url="<g:createLink controller='ajaxSupport' action='setRef'/>",
         data-source="<g:createLink controller='ajaxSupport' action='getRefdata' id='KBComponent.Status'/>">${d.status?.value?:'Not Set'}</span></dd>
  </div>

  <div class="control-group">
    <dt>Roles</dt>
    <dd>
      <ul>
        <g:each in="${d.roles?.sort({"${it.value}"})}" var="t">
          <li>${t.value}</li>
        </g:each>
      </ul>
      <br/>
      Add role: <g:simpleReferenceTypedown name="roleRefdataValue" baseClass="org.gokb.cred.RefdataValue" />
    </dd>
  </div>

  <g:if test="${ d.tags?.size() > 0 }" >
    <dt>Tags</dt>
    <dd>
      <ul>
        <g:each in="${(d.tags as List).sort({"${it.owner.desc}:${it.value}"})}" var="t">
          <li>${t.owner.desc} : ${t.value}</li>
        </g:each>
      </ul>
    </dd>
  </g:if>

  <div class="control-group">
      <dt>Identifiers</dt>
      <dd>
        <ul>
          <g:each in="${d.ids}" var="id">
            <li>${id.namespace.value}:${id.value}</li>
          </g:each>
        </ul>
      </dd>
  </div>

  <g:if test="${d.parent != null}">
    <div class="control-group">
      <dt>Parent</dt>
      <dd><g:link controller="resource" action="show" id="${d.parent.getClassName()+':'+d.parent.id}">${d.parent.name}</g:link></dd>
    </div>
  </g:if>

  <g:if test="${d.children?.size() > 0}">
    <dt>Children</dt>
    <dd>
      <ul>
        <g:each in="${d.children}" var="c">
          <li><g:link controller="resource" action="show" id="${c.getClassName()+':'+c.id}">${c.name}</g:link></li>
        </g:each>
      </ul>
    </dd>
  </g:if>

  <g:if test="${d.getOtherIncomingCombos()?.size() > 0}">
    <dt>Incoming Combos</dt>
    <dd>
      <ul>
        <g:each in="${d.getOtherIncomingCombos()}" var="c">
          <li><g:link controller="resource" action="show" id="${c.fromComponent.getClassName()+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
        </g:each>
      </ul>
    </dd>
  </g:if>

  <g:if test="${d.getOtherOutgoingCombos()?.size() > 0}">
    <dt>Outgoing Combos</dt>
    <dd>
      <ul>
        <g:each in="${d.getOtherOutgoingCombos()}" var="c">
          <li>This Org -- ${c.type?.value} --> <g:link controller="resource" action="show" id="${c.toComponent.getClassName()+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
        </g:each>
      </ul>
    </dd>
  </g:if>
</dl>

<script language="JavaScript">
  $(document).ready(function() {
    $.fn.editable.defaults.mode = 'inline';
    $('.ipe').editable();
  });
</script>
