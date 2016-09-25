<h1>System Alerts...</h1>

  <% if ( updates.size() == 0 ) { %>
    <p> There were no changes detected on your watch list between ${start_date} and ${end_date} </p>
  <% } else { %>
    <p> ${updates.size()} Changes to titles on your watch list between ${start_date} and ${end_date} </p>
    <ul>
      <% updates.each { pkg -> %>
        <li>Package : ${pkg.id} ${pkg.name} ( List owned by ${pkg.owner} )
          <ul>
            <% pkg.titles.each { ti -> %>
              <li>
                Watchlist Title : ${ti.watchlist_title}
                Watchlist Work : ${ti.watchlist_work}
                Matched Title : ${ti.matched_title}
                Matched Tipp: ${ti.matched_tipp}
              </li>
            <% } %>
          </ul>
        </li>
      <% } %>
    </ul>
  <% } %>
