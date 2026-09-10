<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="value">Value</g:annotatedLabel> </dt>
  <dd> ${d?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="namespace">Namespace</g:annotatedLabel> </dt>
  <dd> ${d?.namespace?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="identifiedComponents">Identified Components</g:annotatedLabel> </dt>
  <dd>
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Name</th>
          <th>Type</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.componentLinks}" var="p">
          <tr>
            <td><g:link controller="resource" action="show" id="${p.component.class.name}:${p.component.id}"> ${p.component.name} </g:link></td>
            <td>${p.component.class.simpleName}</td>
            <td>${p.status.value}</td>
          </tr>
        </g:each>
      </tbody>
    </table>
  </dd>
</dl>
