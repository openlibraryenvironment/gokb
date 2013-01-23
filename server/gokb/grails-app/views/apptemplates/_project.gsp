<h1>Project: ${d.name}</h1>

<dl>
  <dt>Internal ID</dt>
  <dd>${d.id}</dd>
  <dt>Project Name</dt>
  <dd>${d.name}</dd>
  <dt>Description</dt>
  <dd>${d.description}</dd>
  <dt>Provider</dt>
  <dd>${d.provider?.name}</dd>
  <dt>Checked In?</dt>
  <dd>${d.checkedIn}</dd>
  <dt>Checked Out By</dt>
  <dd>${d.checkedOutBy}</dd>
  <hr/>
  <dt>Last validation result</dt>
  <dd>${d.lastValidationResult?:'Not yet validated'}</dd>
  <dt>Candidate Rules</dt>
  <dd>
    <table class="table table table-striped">
      <thead>
        <tr>
          <th>Fingerprint</th>
          <th>Rule Scope</th>
          <th>Description</th>
        </tr>
        <tr>
          <th>Rule text</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${d.possibleRulesAsList()}" var="r">
          <tr><td>${r.fingerprint}</td><td>${r.scope}</td><td>${r.description}</td></tr>
          <tr><td colspan="3">${r.ruleJson}</td></tr>
        </g:each>
      </tbody>
    <table>
  </dd>
</dl>

