<r:require modules="gokbstyle"/>
<r:require modules="editable"/>

<h1>Review Request ${d.id}</h1>

<div id="content">
  <ul id="tabs" class="nav nav-tabs">
    <li class="active"><a href="#rrdets" data-toggle="tab">Review Request Details</a></li>
  </ul>
  <div id="my-tab-content" class="tab-content">
    <div class="tab-pane active" id="rrdets">
      <dl class="dl-horizontal">

        <div class="control-group">
          <dt>Internal ID</dt>
          <dd>${d.id?:'New record'}&nbsp;</dd>
        </div>

        <div class="control-group">
          <dt>Cause</dt>
          <dd><g:xEditable class="ipe" owner="${d}" field="descriptionOfCause"/></dd>
        </div>
        <div class="control-group">
          <dt>Review Request</dt>
          <dd><g:xEditable class="ipe" owner="${d}" field="reviewRequest"/></dd>
        </div>


        <div class="control-group">
          <dt>Request Status</dt>
          <dd><g:xEditableRefData owner="${d}" field="status" config='ReviewRequest.Status' /></dd>
        </div>

        <g:if test="${d.id != null}">

          <div class="control-group">
            <dt>Target</dt>
            <dd><g:link controller="resource" action="show" id="${d.componentToReview.getClassName()+':'+d.componentToReview.id}">${d.componentToReview.name?:"Un-named"}</g:link></dd>
          </div>

          <div class="control-group">
            <dt>Request Timestamp</dt>
            <dd>${d.dateCreated}&nbsp;</dd>
          </div>

        </g:if>
        <g:else>
          Additional fields will be available once the record is saved
        </g:else>

    </dl>
  </div>
</div>

