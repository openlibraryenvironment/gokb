
<table class="table table-striped">
 <caption>Search results</caption>
  <thead>
    <tr>
      <g:each in="${qbeConfig.qbeResults}" var="c">
        <th>${c.heading}</th>
      </g:each>
    </tr>
  </thead>
  <tbody>
    <g:each in="${rows}" var="r">
      <tr>
        <g:each in="${qbeConfig.qbeResults}" var="c">
          <td>${groovy.util.Eval.x(r, 'x.' + c.property)}</td>
        </g:each>
      </tr>
    </g:each>
  </tbody>
</table>
