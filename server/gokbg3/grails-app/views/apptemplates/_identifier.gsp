<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="value">Value</g:annotatedLabel> </dt>
  <dd> ${d?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="namespace">Namespace</g:annotatedLabel> </dt>
  <dd> ${d?.namespace?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="identifiedComponents">Identified Components</g:annotatedLabel> </dt>
  <dd>
    <g:render template="/apptemplates/comboList"
      model="${[d:d, property:'identifiedComponents', cols:[[expr:'name',colhead:'Name'],[expr:'status.value',colhead:'Status']]]}" />
  </dd>
</dl>
