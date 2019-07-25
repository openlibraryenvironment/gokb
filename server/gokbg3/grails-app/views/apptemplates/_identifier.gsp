<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="value">Value</g:annotatedLabel> </dt>
  <dd> ${d?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="namespace">Namespace</g:annotatedLabel> </dt>
  <dd> ${d?.namespace?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="identifiedComponents">Identified Components</g:annotatedLabel> </dt>
  <dd>
    <g:render template="/apptemplates/combosByType"
      model="${[d:d, property:'identifiedComponents', combo_status: null, cols:[
                [expr:'fromComponent.name', colhead:'Name', action:'link'],
                [expr:'fromComponent.status.value', colhead: 'Status']]]}" />
  </dd>
</dl>
