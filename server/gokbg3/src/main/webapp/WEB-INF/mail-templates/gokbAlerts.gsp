<h1>GOKb :: Package Change Notifications and Other Alerts...</h1>

  <% if ( updates.size() == 0 ) { %>
    <p> There were no changes detected on your watch list between ${start_date} and ${end_date} </p>
  <% } else { %>
    <p> ${updates.size()} Changes to titles on your watch list between ${start_date} and ${end_date} </p>
    <table>
      <thead>
        <tr>
          <th>Watchlist Title</th>
          <th>Watchlist Work</th>
          <th>Matched Title</th>
          <th>Matched TIPP</th>
          <th>Package</th>
          <th>Action</th>
        </tr>
      </thead>
      <tbody>
        <% updates.each { pkg -> %>
          <tr><td colspan="5"><h2><a href="${serverUrl}/resource/show/org.gokb.cred.Folder%3A${pkg.id}">${pkg.name} ( List owned by ${pkg.owner} )</a></h2></td></tr>
          <% pkg.titles.each { ti -> %>
            <tr>
              <td><a href="${serverUrl}/resource/show/${ti.watchlist_title.class.name}%3A${ti.watchlist_title.id}">${ti.watchlist_title.name}</a></td>
              <td><a href="${serverUrl}/resource/show/${ti.watchlist_work.class.name}%3A${ti.watchlist_work.id}">${ti.watchlist_work.name}</a></td>
              <td><a href="${serverUrl}/resource/show/${ti.matched_title.class.name}%3A${ti.matched_title.id}">${ti.matched_title.name}</a></td>
              <td><a href="${serverUrl}/resource/show/${ti.tipp.class.name}%3A${ti.tipp.id}">TIPP ${ti.tipp.id}</a></td>
              <td><a href="${serverUrl}/resource/show/${ti.tipp.pkg.class.name}%3A${ti.tipp.pkg.id}">${ti.tipp.pkg.name}</a></td>
              <td>
                <% if ( ti.tipp.accessStartDate && ( start_date <= ti.tipp.accessStartDate ) && ( ti.tipp.accessStartDate <= end_date ) ) { %> <strong>ADDED</strong> ${ti.tipp.accessStartDate} <% } %>
                <% if ( ti.tipp.accessEndDate && ( start_date <= ti.tipp.accessEndDate ) && ( ti.tipp.accessEndDate <= end_date ) ) { %> <strong>REMOVED</strong> ${ti.tipp.accessEndDate} <% } %>
              </td>
            </tr>
          <% } %>
        <% } %>
      </tbody>
    </table>
  <% } %>
