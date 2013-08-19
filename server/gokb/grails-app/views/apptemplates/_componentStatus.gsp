<dl class="dl-horizontal">

  <div class="control-group">
    <dt>Date Created</dt>
    <dd>
      ${d?.dateCreated?:'&nbsp;'}
    </dd>
  </div>

  <div class="control-group">
    <dt>Last Updated</dt>
    <dd>
      ${d?.lastUpdated?:'&nbsp;'}
    </dd>
  </div>

  <div class="control-group">
    <dt>Last updated by</dt>
    <dd>
      ${d?.lastUpdatedBy?.displayName?:'&nbsp;'}
    </dd>
  </div>

</dl>
