<h1>Platform: ${d.name}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Platform Name</dt>
  <dd>${d.name}</dd>
  <dt>Tags</dt>
  <dd>
    <ul>
      <g:each in="${d.tags}" var="t">
        <li>${t.value}</li>
      </g:each>
    </ul>
  </dd>
</dl>

