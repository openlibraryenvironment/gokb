
<g:set var="counter" value="${offset}" />

<table class="table table-striped">
 <caption>Search results</caption>
  <thead>
    <tr>
      <th></th>
      <g:each in="${qbeConfig.qbeResults}" var="c">
        <th>${c.heading}</th>
      </g:each>
      <th>Actions</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${rows}" var="r">
      <tr>
        <td>${++counter}</td>
        <g:each in="${qbeConfig.qbeResults}" var="c">
          <td>${groovy.util.Eval.x(r, 'x.' + c.property)}</td>
        </g:each>
        <td>
          <g:link class="btn" controller="resource" action="show" id="${r.class.name+':'+r.id}">Show</g:link>
          <g:link class="btn" controller="search" action="index" params="${params+['det':counter]}">Preview -></g:link>
        </td>
      </tr>
    </g:each>
  </tbody>
</table>
