<h1>TIPP ${d.id}</h1>
<p>occurrence of ${d.title.name} in Package ${d.pkg.name} under platform ${d.platform.name}<p>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>

  <dt>Other Incoming Combos</dt>
  <dd>
    <ul>
      <g:each in="${d.incomingCombos}" var="c">
        <li><g:link controller="resource" action="show" id="${c.from.class.name+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
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
