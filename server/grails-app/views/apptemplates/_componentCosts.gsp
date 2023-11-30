<table class="table table-bordered">
  <thead>
    <tr>
      <th>Price Type</th>
      <th>Start Date</th>
      <th>End Date</th>
      <th>Currency</th>
      <th>Price</th>
    </tr>
  </thead>
  <tbody>
    <g:each in="${d?.prices}" var="p">
      <tr>
        <td>${p.priceType?.value}</td>
        <td>${p.startDate}</td>
        <td>${p.endDate}</td>
        <td>${p.currency?.value}</td>
        <td>${p.price}</td>
      </tr>
    </g:each>
  </tbody>
</table>

