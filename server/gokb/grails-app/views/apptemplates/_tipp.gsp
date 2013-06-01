<h1>TIPP ${d.id}</h1>
<p>occurrence of ${d.title.name} in Package ${d.pkg.name} under platform ${d.hostPlatform.name}<p>

<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Internal ID</dt>
    <dd>${d.id}</dd>
  </div>

  <div class="control-group">
    <dt>Title</dt>
    <dd><g:link controller="resource" action="show" id="${d.title?.class.name+':'+d.title?.id}">${d.title?.name}</g:link></dd>
  </div>

  <div class="control-group">
    <dt>Package</dt>
    <dd><g:link controller="resource" action="show" id="${d.pkg?.class.name+':'+d.pkg?.id}">${d.pkg?.name}</g:link></dd>
  </div>

  <div class="control-group">
    <dt>Platform</dt>
    <dd><g:link controller="resource" action="show" id="${d.hostPlatform?.class.name+':'+d.hostPlatform?.id}">${d.hostPlatform?.name}</g:link></dd>
  </div>

  <div class="control-group">
    <dt>Coverage</dt>
    <dd>
      <table class="table table-striped">
        <thead>
          <tr>
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
          <tr>
            <td>${d.startDate}</td>
            <td>${d.startVolume}</td>
            <td>${d.startIssue}</td>
            <td>${d.endDate}</td>
            <td>${d.endVolume}</td>
            <td>${d.endIssue}</td>
            <td>${d.embargo}</td>
          </tr>
        </tbody>
      </table>
    </dd>
  </div>

  <div class="control-group">
    <dt>Coverage Depth</dt>
    <dd>${d.coverageDepth}</dd>
  </div>

  <div class="control-group">
    <dt>Coverage Note</dt>
    <dd>${d.coverageNote}</dd>
  </div>

  <div class="control-group">
    <dt>Incoming Combos</dt>
    <dd>
      <ul>
        <g:each in="${d.getOtherIncomingCombos()}" var="c">
        	
          <li><g:link controller="resource" action="show" id="${c.fromComponent.class.name+':'+c.fromComponent.id}">${c.fromComponent.name}</g:link> has ${c.type?.value} This Org</li>
        </g:each>
      </ul>
    </dd>
  </div>

  <div class="control-group">
    <dt>Outgoing Combos</dt>
    <dd>
      <ul>
        <g:each in="${d.getOtherOutgoingCombos()}" var="c">
          <li>This Org has ${c.type?.value} <g:link controller="resource" action="show" id="${c.toComponent.class.name+':'+c.toComponent.id}">${c.toComponent.name}</g:link></li>
        </g:each>
      </ul>
    </dd>
  </div>

</dl>
