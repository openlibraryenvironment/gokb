<table class="table table-bordered">
  <thead>
    <tr><th>Property</th><th>Value</th></tr>
  </thead>
  <tbody>
    <g:each in="${d.additionalProperties}" var="cp">
      <tr>
        <td>${cp.propertyDefn.propertyName}</td>
        <td>${cp.apValue}</td>
      </tr>
    </g:each>
  </tbody>
</table>

