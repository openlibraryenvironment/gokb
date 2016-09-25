<h1>System Alerts...</h1>

<ul>
  <% updates.each { pkg -> %>
    <li>Package : ${pkg}
    </li>
  <% } %>
</ul>
