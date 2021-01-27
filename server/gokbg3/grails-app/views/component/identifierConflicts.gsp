<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Identifier Review</title>
</head>
<body>
  <h1 class="page-header">Identifier Review</h1>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-body">
      <dl class="dl-horizontal">
        <g:form controller="component" action="identifierConflicts" class="form-horizontal">
          <div class="input-group">
            <dt class="dt-label">Identifier Namespace</dt>
            <dd>
              <g:simpleReferenceTypedown class="form-control" name="id" baseClass="org.gokb.cred.IdentifierNamespace" value="${namespace ? 'org.gokb.cred.IdentifierNamespace:' + namespace.id : ''}" />
            </dd>
            <dt class="dt-label">Conflict type</dt>
            <dd>
              <select class="form-control" id="ctype" name="ctype">
                <option value="st" ${ctype == 'st' ? 'selected' : ''}>Multiple occurrences of one namespace on one title</option>
                <option value="di" ${ctype == 'di' ? 'selected' : ''}>Identifers connected to multiple components</option>
              </select>
            </dd>
            <span class="input-group-btn">
              <button type="submit" class="btn btn-default" >Search</button>
            </span>
          </div>
        </g:form>
      </dl>
    </div>
  </div>
  <g:if test="${namespace}">
    <g:if test="${ctype == 'st'}">
      <h1 class="page-header">Components with multiple IDs of namespace <g:link controller="resource" action="show" id="org.gokb.cred.IdentifierNamespace:${namespace.id}">${namespace.value}</g:link> (${titleCount})</h1>
      <div id="mainarea" class="panel panel-default">

        <g:if test="${singleTitles.size() > 0}">
          <table class="table table-striped table-condensed table-bordered">
            <thead style="white-space:nowrap;">
              <tr class="inline-nav">
                <th>Component</th>
                <th>Identifiers</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${singleTitles}" var="st">
                <tr>
                  <td>
                    <g:link controller="resource" action="show" id="${st.uuid}">${st.name}</g:link>
                    <ul>
                      <li>Edit Status: ${st.editStatus?.value ?: 'Not Set'}</li>
                      <li>Latest Publisher: ${st.currentPublisher?.name ?: 'None'}</li>
                    </ul>
                  </td>
                  <td>
                    <ul>
                    <g:each in="${st.ids}" var="cid">
                      <li><span style="${cid.namespace.value == namespace.value ?'font-weight:bold;':''}">${cid.namespace.value}:${cid.value}</span></li>
                    </g:each>
                    </ul>
                  </td>
                </tr>
              </g:each>
            </tbody>
          </table>
          <div class="pagination" style="text-align:center">
            <g:if test="${titleCount?:0 > 0 }" >
              <g:paginate
                controller="component"
                action="identifierConflicts"
                params="${[ctype: ctype, id: params.id]}"
                next="Next"
                prev="Prev"
                max="${max}"
                total="${titleCount}"
              />
            </g:if>
          </div>
        </g:if>
        <g:else>
          <div style="text-align:center">
            <div class="alert alert-info" style="display:inline-block;font-weight:bolder;margin:10px;">No occurrences found!</div>
          </div>
        </g:else>
      </div>
    </g:if>

    <g:if test="${ctype == 'di'}">
      <h1 class="page-header">Identifiers connected to multiple components for namespace <g:link controller="resource" action="show" id="org.gokb.cred.IdentifierNamespace:${namespace.id}">${namespace.value}</g:link> (${idsCount})</h1>
      <div id="mainarea" class="panel panel-default">

        <g:if test="${dispersedIds.size() > 0}">
          <table class="table table-striped table-condensed table-bordered">
            <thead>
              <tr>
                <th>Identifier</th>
                <th>Identified Components</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${dispersedIds}" var="did">
                  <tr>
                    <td><g:link controller="resource" action="show" id="${did.uuid}"><span style="white-space:nowrap">${did.value}</span></g:link></td>
                    <td>
                      <g:each in="${did.identifiedComponents}" var="idc">
                        <div><g:link controller="resource" action="show" id="${idc.uuid}">${idc.name} (${idc.status?.value ?: 'Unknown Status'})</g:link></div>
                      </g:each>
                    </td>
                  </tr>
              </g:each>
            </tbody>
          </table>
          <div class="pagination" style="text-align:center">
            <g:if test="${idsCount?:0 > 0 }" >
              <g:paginate
                controller="component"
                action="identifierConflicts"
                params="${[ctype: ctype, id: params.id]}"
                next="Next"
                prev="Prev"
                max="${max}"
                total="${idsCount}"
              />
            </g:if>
          </div>
        </g:if>
        <g:else>
          <div style="text-align:center">
            <div class="alert alert-info" style="display:inline-block;font-weight:bolder;margin:10px;">No occurrences found!</div>
          </div>
        </g:else>
      </div>
    </g:if>
  </g:if>

</body>
</html>
