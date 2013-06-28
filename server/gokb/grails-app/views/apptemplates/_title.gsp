<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Title: ${d.name}</h1>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Internal ID</dt>
    <dd>${d.id}</dd>
  </div>

  <div class="control-group">
    <dt>Title</dt>
    <dd>${d.name}</dd>
  </div>

  <div class="control-group">

    <dt>Publishers</dt>
    <dd>
      <g:if test="${d.publisher}">
        <table class="table table-striped">
          <thead><tr><th>Publisher Name</th><th>Relationship Status</th><th>Publisher From</th><th>Publisher To</th></tr></thead>
          <tbody>
            <g:each in="${d.getCombosByPropertyName('publisher')}" var="p">
              <tr>
                <td><g:link controller="resource" action="show" id="${p.toComponent.class.name}:${p.toComponent.id}">${p.toComponent.name}</g:link></td>
                <td>${p.status.value}</td>
                <td><g:xEditable class="ipe" owner="${p}" field="startDate" type="date"/></td>
                <td><g:xEditable class="ipe" owner="${p}" field="endDate" type="date"/></td>
              </tr>
            </g:each>
          </tbody>
        </table>
      </g:if>
    </dd>
  </div>

  <div class="control-group">

    <dt>Identifiers</dt>
    <dd>
      <ul>
        <g:each in="${d.ids}" var="id">
          <li>${id.namespace.value}:${id.value}</li>
        </g:each>
      </ul>
    </dd>
  </div>

  <div class="control-group">

    <dt>Tags</dt>
    <dd>
      <g:if test="${(d.tags != null) && ( d.tags.size() > 0 )}">
        <ul>
          <g:each in="${d.tags}" var="t">
            <li>${t.value}</li>
          </g:each>
        </ul>
      </g:if>
      <g:else> 
        &nbsp;
      </g:else>
    </dd>
  </div>

  <div class="control-group">
    <dt>Package Appearences</dt>
    <dd>
      <table class="table table-striped">
        <thead>
          <tr>
            <th>Package</th>
            <th>Platform</th>
            <th>Start Date</th>
            <th>Start Volume</th>
            <th>Start Issue</th>
            <th>End Date</th>
            <th>End Volume</th>
            <th>End Issue</th>
            <th>Embargo</th>
          </tr>
        </thead>
        <tbody>
          <g:each in="${d.tipps}" var="tipp">
            <tr>
              <td><g:link controller="resource" action="show" id="${tipp.pkg.getClassName()+':'+tipp.pkg.id}">${tipp.pkg.name}</g:link></td>
              <td><g:link controller="resource" action="show" id="${tipp.hostPlatform.getClassName()+':'+tipp.hostPlatform.id}">${tipp.hostPlatform.name}</g:link></td>
              <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${tipp.startDate}"/></td>
              <td>${tipp.startVolume}</td>
              <td>${tipp.startIssue}</td>
              <td><g:formatDate format="${session.sessionPreferences?.globalDateFormat}" date="${tipp.endDate}"/></td>
              <td>${tipp.endVolume}</td>
              <td>${tipp.endIssue}</td>
              <td>${tipp.embargo}</td>
            </tr>
          </g:each>
        </tbody>
      </table>
    </dd>
  </div>

  <div class="control-group">

    <dt>Other Links (Out)</dt>
    <dd>
      <ul>
        <g:each in="${d.getOtherIncomingCombos()}" var="c">
          <li><g:link controller="resource" action="show" id="${c.fromComponent.getClassName()+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> -- ${c.type?.value} --> This Org</li>
        </g:each>
      </ul> &nbsp;
    </dd>
  </div>

  <div class="control-group">

    <dt>Other Links (In)</dt>
    <dd>
      <ul>
        <g:each in="${d.getOtherOutgoingCombos()}" var="c">
          <li>This Title -- ${c.type?.value} -->  <g:link controller="resource" action="show" id="${c.toComponent.getClassName()+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
        </g:each>
      </ul> &nbsp;
    </dd>
  </div>
</dl>

