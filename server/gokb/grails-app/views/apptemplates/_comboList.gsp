<table class="table table-striped">
  <thead>
    <tr>
      <g:each in="${colheads}" var="ch">
        <th>${ch}</th>
      </g:each>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d[property]}" var="row">
      <tr>
        <g:each in="${cols}" var="c">
          <td>${groovy.util.Eval.x(row, 'x.' + c)}</td>
        </g:each>
      </tr>
    </g:each>
  </tbody>
</table>
