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

        <dt> Provider </dt> <dd> ${pkg.provider?.name} </dd>
        <dt> Status </dt> <dd> ${pkg.status?.value} </dd>
        <dt> Description </dt> <dd> ${pkg.description}</dd>
        <dt> URL </dt> <dd> ${pkg.descriptionURL}</dd>

        <g:if test="${pkg.prices?.size() > 0}">
          <h2>Price Information</h2>
          <table class="table table-striped table-bordered">
            <thead>
              <tr> 
                <th>Price Type</th>
                <th>Currency</th>
                <th>Start Date</th>
                <th>End Date</th>
                <th>Price</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${pkg.prices}" var="p">
                <tr>
                  <td>${p.priceType?.value}</td>
                  <td>${p.currency?.value}</td>
                  <td><g:formatDate date="${p.startDate}" format="yyyy-MM-dd"/></td>
                  <td><g:formatDate date="${p.endDate}" format="yyyy-MM-dd"/></td>
                  <td>${p.price}</td>
                </tr>
              </g:each>
            </tbody>
          </table>
        </g:if>

        <h2>Curated By</h2>
        <ul>
          <g:each in="${pkg.curatoryGroups}" var="cg">
            <li><g:link controller="public" action="curatorDetails" params="${cg.id}">${cg.name}</g:link></li>
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
                <td><g:link controller="public" action="titleDetails" params="${t.title.id}">${t.title.name}</g:link></th>
                <td>
                  <ul>
                    <g:each in="${t.title.ids}" var="id">
                      <li><strong>${id.namespace.value}</strong> : ${id.value}</li>
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
