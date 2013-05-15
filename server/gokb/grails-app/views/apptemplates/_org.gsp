<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Organisation: ${d.name}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Org Name</dt>
  <dd><g:xEditable owner="${d}" field="name">${d.name}</g:xEditable></dd>
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
	<g:if test="${ d.ids?.size() > 0}" >
	  <dt>Identifiers</dt>
	  <dd>
	    <ul>
	      <g:each in="${d.ids}" var="id">
	        <li>${id.identifier.ns.ns}:${id.identifier.value}</li>
	      </g:each>
	    </ul>
	  </dd>
	</g:if>
  <g:if test="${d.parent != null}">
	  <dt>Parent</dt>
	  <dd>${d.parent}</dd>
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

