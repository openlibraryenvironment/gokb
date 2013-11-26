<dl class="dl-horizontal">

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="dateCreated">Date Created</g:annotatedLabel></dt>
    <dd>
      ${d?.dateCreated?:'&nbsp;'}
    </dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="lastUpdated">Last Updated</g:annotatedLabel></dt>
    <dd>
      ${d?.lastUpdated?:'&nbsp;'}
    </dd>
  </div>

  <div class="control-group">
    <dt><g:annotatedLabel owner="${d}" property="lastUpdatedBy">Last updated by</g:annotatedLabel></dt>
    <dd>
      ${d?.lastUpdatedBy?.displayName?:'&nbsp;'}
    </dd>
  </div>

</dl>
