<h1>Organisation: ${d.name}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Org Name</dt>
  <dd>${d.name}</dd>
  <dt>Tags</dt>
  <dd>
    <ul>
      <g:each in="${d.tags}" var="t">
        <li>${t.owner.desc} : ${t.value}</li>
      </g:each>
    </ul>
  </dd>
  <dt>Identifiers</dt>
  <dd>
    <ul>
      <g:each in="${d.ids}" var="id">
        <li>${id.identifier.ns.ns}:${id.identifier.value}</li>
      </g:each>
    </ul>
  </dd>
  <dt>Incoming Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.incomingCombos}" var="c">
        <li><g:link controller="resource" action="show" id="${c.fromComponent.class.name+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
      </g:each>
    </ul>
  </dd>
  <dt>Outgoing Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.outgoingCombos}" var="c">
        <li>This Org -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.class.name+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
      </g:each>
    </ul>
  </dd>
</dl>

