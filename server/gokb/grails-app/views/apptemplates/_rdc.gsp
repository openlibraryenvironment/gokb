<h1>Refdata Category: ${d.desc}</h1>


<table class="table table-bordered table-striped" style="clear: both"><tbody>
  <tr><td>Internal Id</td>            <td>${d.id}</td></tr>
  <tr><td>Description</td>            <td>${d.desc}</td></tr>
  <tr><td>Values</td>                 <td>
    <ul>
      <g:each in="${d.values}" var="v">
        <li>${v.value}</li>
      </g:each>
    </ul>
  </td></tr>
</table>
