<div class="panel panel-default">

  <!-- Default panel contents -->
  <div class="panel-heading modal-header">
    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
    <h3 class="modal-title">Notes for ${params.id}</h3>
  </div>

  <div class="panel-body modal-body">
    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Note ID</th>
          <th>Agent</th>
          <th>Date</th>
          <th>Note</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${noteLines}" var="n">
          <tr>
            <td>${n.id}</td>
            <td style="white-space:nowrap;">${n.creator.username}</td>
            <td style="white-space:nowrap;">${n.dateCreated}</td>
            <td style="white-space:nowrap;">${n.note}</td>
          </tr>
        </g:each>
      </tbody>
    </table>

    <h4>Add a note</h4>
    <dl class="dl-horizontal">
      <g:form controller="ajaxSupport" action="addToCollection" role="form" class="form">
        <input type="hidden" name="__context" value="${ownerClass}:${ownerId}"/>
        <input type="hidden" name="__newObjectClass" value="org.gokb.cred.Note"/>
        <input type="hidden" name="ownerClass" value="${ownerClass}"/>
        <input type="hidden" name="ownerId" value="${ownerId}"/>
        <input type="hidden" name="creator" value="org.gokb.cred.User:${user.id}"/>
        <textarea style="width:100%" class="input-xxlarge" name="note"></textarea>
        <button type="submit" class="btn btn-default btn-primary btn-sm pull-right">Add</button>
      </g:form>
    </dl>
  </div>
</div>
