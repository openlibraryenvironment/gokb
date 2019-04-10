<dl class="dl-horizontal">
  <dt> <g:annotatedLabel owner="${d}" property="key">Key</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="key" /> </dd>

  <dt> <g:annotatedLabel owner="${d}" property="locale">Locale</g:annotatedLabel> </dt>
  <dd> ${d?.locale?.value} </dd>

  <dt> <g:annotatedLabel owner="${d}" property="content">Content</g:annotatedLabel> </dt>
  <dd> <g:xEditable class="ipe" owner="${d}" field="content" /> </dd>
</dl>
