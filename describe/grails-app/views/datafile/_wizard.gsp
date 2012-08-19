<div class="row-fluid">
  <div class="wizard">
    <g:link class="${actionName=='create'?'current':''}" controller="datafile" action="create" id="${params.id}"><span class="badge badge-inverse">1</span>Source File</g:link>
    <g:link class="${actionName=='identification'?'current':''}" controller="datafile" action="identification" id="${params.id}"><span class="badge badge-warning">2</span>Identification</g:link>
    <g:link class="${actionName=='chunking'?'current':''}" controller="datafile" action="chunking" id="${params.id}"><span class="badge badge-warning">2.1</span>Chunking</g:link>
    <g:link class="${actionName=='fileMetadata'?'current':''}" controller="datafile" action="fileMetadata" id="${params.id}"><span class="badge badge-warning">3</span>File Metadata</g:link>
    <g:link class="${actionName=='columns'?'current':''}" controller="datafile" action="columns" id="${params.id}"><span class="badge badge-warning">4</span>Columns</g:link>
    <g:link class="${actionName=='firstPassData'?'current':''}" controller="datafile" action="firstPassData" id="${params.id}"><span class="badge badge-warning">5</span>First Pass Data</g:link>
    <g:link class="${actionName=='rules'?'current':''}" controller="datafile" action="rules" id="${params.id}"><span class="badge badge-warning">6</span>Rules</g:link>
    <g:link class="${actionName=='finalData'?'current':''}" controller="datafile" action="finalData" id="${params.id}"><span class="badge badge-warning">7</span>Final Data</g:link>
  </div>
</div>
