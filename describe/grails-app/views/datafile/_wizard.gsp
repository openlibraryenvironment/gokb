<div class="row-fluid">
  <div class="wizard">
    <g:link class="${actionName=='create'?'current':''}" controller="datafile" action="create" id="${params.id}">
      <span class="badge ${actionName=='create'?'badge-inverse':'badge-warning'}">1</span>&nbsp;Source File
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='identification'?'current':''}" controller="datafile" action="identification" id="${params.id}">
      <span class="badge ${actionName=='identification'?'badge-inverse':'badge-warning'}">2</span>&nbsp;Identification
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='chunking'?'current':''}" controller="datafile" action="chunking" id="${params.id}">
      <span class="badge ${actionName=='chunking'?'badge-inverse':'badge-warning'}">2.1</span>&nbsp;Chunking
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='fileMetadata'?'current':''}" controller="datafile" action="fileMetadata" id="${params.id}">
      <span class="badge ${actionName=='fileMetadata'?'badge-inverse':'badge-warning'}">3</span>&nbsp;File Metadata
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='columns'?'current':''}" controller="datafile" action="columns" id="${params.id}">
      <span class="badge ${actionName=='columns'?'badge-inverse':'badge-warning'}">4</span>&nbsp;Columns
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='firstPassData'?'current':''}" controller="datafile" action="firstPassData" id="${params.id}">
      <span class="badge ${actionName=='firstPassData'?'badge-inverse':'badge-warning'}">5</span>&nbsp;Input Data
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='rules'?'current':''}" controller="datafile" action="rules" id="${params.id}">
      <span class="badge ${actionName=='rules'?'badge-inverse':'badge-warning'}">6</span>&nbsp;Rules
    </g:link>&nbsp;&gt;
    <g:link class="${actionName=='finalData'?'current':''}" controller="datafile" action="finalData" id="${params.id}">
      <span class="badge ${actionName=='finalData'?'badge-inverse':'badge-warning'}">7</span>&nbsp;Enhanced Data
    </g:link>
  </div>
</div>
