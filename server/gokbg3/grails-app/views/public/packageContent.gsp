<!DOCTYPE html>
<html>
<head>
<meta name='layout' content='public' />
<title>GOKb: Packages</title>
</head>

<body>

  <div class="container">

    <div class="row"> <div class="box">
   
     <div class="col-lg-12 ">
      <div class="well">
      <g:if test="${pkgName}">

        <h1>Package: ${pkgName}</h1>
        <h2>Curated By</h2>
        <ul>
          <g:each in="${pkg.curatoryGroups}" var="cg">
            <li>${cg.name}</li>
          </g:each>
        </ul>

        <h2>Titles (${titleCount})</h2>
        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Title</th>
              <th>Identifiers</th>
              <th>Coverage</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${tipps}" var="t">
              <tr>
                <td>${t.title.name}</th>
                <td>
                  <ul>
                    <g:each in="${t.title.ids}" var="id">
                      <li<strong>${id.namespace.value}</strong> : ${id.value}</li>
                    </g:each>
                  </ul>
                </th>
                <td>
                  ${t.coverageDepth?.value}<br/>${t.coverageNote}
                </th>
              </tr>
            </g:each>
          </tbody>
        </table>

        <div class="pagination" style="text-align:center">
          <g:if test="${titleCount?:0 > 0 }" >
            <g:paginate  controller="public" action="packageContent" params="${params}" next="Next" prev="Prev" max="${max}" total="${titleCount}" />
          </g:if>
        </div>

      </g:if>

      </div>
      </div>
    </div></div>

  </div>
</body>
</html>
