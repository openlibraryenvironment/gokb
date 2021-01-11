<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="value">Value</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="value" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="name">Name</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="name" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="datatype">RDF Datatype</g:annotatedLabel> </dt>
  <dd> <g:xEditableRefData owner="${d}" field="datatype" config='RDFDataType' /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="family">Category</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="family" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="pattern">Pattern</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="pattern" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="targetType">Target Type</g:annotatedLabel> </dt>
  <dd> <g:xEditableRefData owner="${d}" field="targetType" config='IdentifierNamespace.TargetType' /> </dd>
</dl>
