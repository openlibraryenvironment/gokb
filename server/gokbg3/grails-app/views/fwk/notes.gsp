<div class="panel panel-default">

  <!-- Default panel contents -->
  <div class="panel-heading modal-header">
    <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
    <h3 class="modal-title">Notes for ${params.id}</h3>
  </div>

  <div class="panel-body modal-body">

    <h4>New note</h4>

    <g:form name="newNoteForm" controller="ajaxSupport" action="addToCollection" role="form" class="form">
      <input type="hidden" name="__context" value="${ownerClass}:${ownerId}"/>
      <input type="hidden" name="__newObjectClass" value="org.gokb.cred.Note"/>
      <input type="hidden" name="ownerClass" value="${ownerClass}"/>
      <input type="hidden" name="ownerId" value="${ownerId}"/>
      <input type="hidden" name="creator" value="org.gokb.cred.User:${user.id}"/>
      <div class="input-group">
        <textarea class="form-control text-complete" style="resize:none;" rows="5" name="note"></textarea>
        <span class="btn btn-default btn-primary btn-sm input-group-addon" onClick="document.forms['newNoteForm'].submit()">Add</span>
      </div>
    </g:form>
    <br/>

    <table class="table table-striped table-bordered">
      <thead>
        <tr>
          <th>Date</th>
          <th>Agent</th>
          <th>Note</th>
        </tr>
      </thead>
      <tbody>
        <g:each in="${noteLines}" var="n">
          <tr>
            <td style="white-space:nowrap;">${n.dateCreated}</td>
            <td style="white-space:nowrap;">${n.creator.username}</td>
            <td width="100%">${n.note}</td>
          </tr>
        </g:each>
      </tbody>
    </table>

  </div>
</div>

