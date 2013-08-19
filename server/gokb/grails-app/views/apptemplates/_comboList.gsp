<table class="table table-striped table-bordered">
  <thead>
    <tr>
      <g:each in="${cols}" var="ch">
        <th>${ch.colhead}</th>
      </g:each>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d[property]}" var="row">
      <tr>
        <g:each in="${cols}" var="c">
          <td>${groovy.util.Eval.x(row, 'x.' + c.expr)}</td>
        </g:each>
      </tr>
    </g:each>
  </tbody>
</table>
