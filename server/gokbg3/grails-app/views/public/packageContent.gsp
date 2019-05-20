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

        <h1>Package <span style="font-weight:bolder;">${pkgName}</span></h1>
        <dl class="dl-horizontal" style="margin-left:-115px;">
          <dt> Provider </dt> <dd> ${pkg.provider?.name} </dd>
          <dt> Status </dt> <dd> ${pkg.status?.value} </dd>
          <dt> Description </dt> <dd> ${pkg.description}</dd>
          <dt> URL </dt> <dd> ${pkg.descriptionURL}</dd>
          <dt> UUID </dt> <dd> ${pkg.uuid} </dd>
        </dl>
        <div style="clear:both;">

          <g:link controller="packages" action="kbart" id="${params.id}">KBart File</g:link> &nbsp;
          <g:link controller="packages" action="packageTSVExport" id="${params.id}">GOKb File</g:link>
        </div>
        <div style="margin-top:10px;">
          <g:link controller="resource" action="show" id="${pkg.uuid}">Switch to editing view (Login required)</g:link>
        </div>
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
