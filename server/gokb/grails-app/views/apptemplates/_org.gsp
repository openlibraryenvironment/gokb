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

</dl>

