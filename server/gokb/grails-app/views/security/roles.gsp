<table class="table table-bordered">
  <tr>
    <th>Roles</th>
    <th>Status</th>
  </tr>
  <g:each in="${ currentRoles }" var="roleName, status">
    <tr>
      <td>
        ${ roleName }
      </td>
      <td>
        ${ status ? Member : Not Member }
      </td>
    </tr>
  </g:each>
</table>