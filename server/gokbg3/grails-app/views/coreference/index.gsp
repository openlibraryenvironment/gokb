<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Coreference Service</title>
</head>
<body>
  <h1 class="page-header">Coreference Service</h1>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-heading">
      <h3 class="panel-title">Identifier Search</h3>
    </div>
    <div class="panel-body">
      <form action="index">
        <div class="col-md-5" >
          <label for="nspart">Namespace:</label>
          <input type="text" name="nspart" id="nspart" class="form-control" value="${params.nspart}" placeholder="Namespace" />
        </div>
        <div class="col-md-7" >
          <label for="idpart">Identifier:</label>
          <div class="input-group">
            <input type="text" name="idpart" id="idpart" class="form-control" value="${params.idpart}" placeholder="Identifier" />
            <span class="input-group-btn">
              <button type="submit" class="btn btn-default"  >Go</button>
            </span>
          </div>
        </div>
      </form>
    </div>
    <div class="panel-footer">
      <p>
        Enter an identifier above to find the information GOKb holds about
        that item.<br /> Identifiers can take the following forms:
      </p>
      <ul>
        <li>a context free identifier, eg "1600-0390" (Without quotes)
          - Will find any occurrence of this string used as an identifier,
          and could expect to return information about a title with name
          "ACTA ARCHAEOLOGICA". Other items which share this identifier might
          also be returned. In this case, this is unlikely since the eISSN
          structure is unique. However if we look at, for example, org
          identifiers from the NCSU orgs database, we see "American Chemical
          Society" has the ID "2". There will be many items in GOKb who's
          unqualified identifier is "2" and this query will return many
          objects.</li>
        <li>an identifier with associated namespace, eg <g:link
            controller="coreference" action="index"
            params="${[nspart:'eissn',idpart:'1600-0390']}">Namespace "eissn", Identifier "1600-0390" (Without quotes)</g:link>
          - <g:link controller="coreference" action="index"
            params="${[nspart:'eissn',idpart:'1600-0390',format:'json']}">[json]</g:link>
          - <g:link controller="coreference" action="index"
            params="${[nspart:'eissn',idpart:'1600-0390',format:'xml']}">[xml]</g:link>
          - will lookup specific instances of "ACTA ARCHAEOLOGICA". Searching
          for <g:link controller="coreference" action="index"
            params="${[nspart:'ncsu-internal',idpart:'ncsu:2']}">Namespace "ncsu-internal", Identifier "ncsu:2" (Without quotes)</g:link>
          <g:link controller="coreference" action="index"
            params="${[nspart:'ncsu-internal',idpart:'ncsu:2',format:'json']}">[json]</g:link>
          <g:link controller="coreference" action="index"
            params="${[nspart:'ncsu-internal',idpart:'ncsu:2',format:'xml']}">[xml]</g:link>
          will lookup specific occurences within the ncsu-internal namespace. <br/>
                                        More detail can be found in <a href="https://github.com/k-int/gokb-phase1/wiki/Co-referencing-Detail">the github wiki page for coreferencing</a>`
        </li>
      </ul>
    </div>
    <g:if test="${matched_identifiers}">
      <table class="table table-striped table-condensed table-bordered">
        <thead>
          <tr>
            <th colspan="3">Located ${count} objects for identifier "${params.idpart}"
              (namespace:${params.nspart?:'None'})
            </th>
          </tr>
          <tr>
            <th>GOKb Canonical</th>
            <th>Name/Title</th>
            <th>External Identifiers</th>
          </tr>
        </thead>
        <tbody>
          <g:each in="${matched_identifiers}" var="i2">
            <g:each in="${i2.records}" var="i">
              <tr>
                <td><g:link controller="resource" action="show"
                    id="${i.getClassName()}:${i.id}">
                    ${i.getClassName()}:${i.id}
                  </g:link></td>
                <td><g:link controller="resource" action="show"
                    id="${i.getClassName()}:${i.id}">
                    ${i.name}
                  </g:link></td>
                <td><g:each in="${i.ids}" var="sa">
                    <g:link controller="coreference" action="index"
                      params="${[nspart:sa.namespace.value,idpart:sa.value]}">
                      ${sa.namespace.value}:${sa.value}
                    </g:link>
                    <br />
                  </g:each>
                </td>
              </tr>
            </g:each>
          </g:each>
        </tbody>
      </table>
    </g:if>
  </div>
</body>
</html>
