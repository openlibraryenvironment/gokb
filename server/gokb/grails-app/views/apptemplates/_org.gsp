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
        <li><g:link controller="resource" action="show" id="${c.from.class.name+':'+c.from.id}">${c.from.name}</g:link> -- ${c.type?.value} --> This Org</li>
      </g:each>
    </ul>
  </dd>
  <dt>Outgoing Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.outgoingCombos}" var="c">
        <li>This Org -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.to.class.name+':'+c.to.id}">${c.to.name}</g:link></li>
      </g:each>
    </ul>
  </dd>
</dl>

