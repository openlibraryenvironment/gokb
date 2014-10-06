<div class="modal-header">
	<button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
	<h3 class="modal-title">Notes ${params.id}</h3>
</div>
<div class="modal-body">
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
  <hr/>
  <h4>Add a note</h4>
  <dl class="dl-horizontal">
    <g:form controller="ajaxSupport" action="addToCollection" class="form-inline">
      <input type="hidden" name="__context" value="${ownerClass}:${ownerId}"/>
      <input type="hidden" name="__newObjectClass" value="org.gokb.cred.Note"/>
      <input type="hidden" name="ownerClass" value="${ownerClass}"/>
      <input type="hidden" name="ownerId" value="${ownerId}"/>
      <input type="hidden" name="creator" value="org.gokb.cred.User:${user.id}"/>
      <dt>Note Text</dt><dd><textarea name="note"></textarea></dd>
      <dt></dt><dd><button type="submit" class="btn btn-default btn-primary btn-sm ">Add</button></dd>
    </g:form>
  </dl>
</div>
<div class="modal-footer">
	<button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
</div>
