<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: ${displayobj?.getNiceName() ?: 'Component'} 
<g:if test="${displayobj}">
&lt;${ displayobj?.isEditable() ? 'Editable' : 'Read Only' }&gt; 
&lt;${ displayobj?.isCreatable() ? 'Creatable' : 'Not Creatable' }&gt;
</g:if>
<g:else>
&lt;Not found&gt;
</g:else>
</title>
</head>
<body>
<br/>
<nav class="navbar navbar-inverse">
  <div class="container-fluid">
    <div class="navbar-header">
      <span class="navbar-brand">
        <g:if test="displayobj?.id != null">
          ${displayobj?.getNiceName() ?: 'Component'} : ${displayobj?.id}
          <g:if test="${ displayobj?.respondsTo('getDisplayName') && displayobj.getDisplayName()}"> - <strong>${displayobj.getDisplayName()}</strong></g:if>
          <g:if test="${ !displayobj?.isEditable() }"> <small><i>&lt;Read only&gt;</i></small> </g:if>
        </g:if>
        <g:else>
          Create New ${displayobj?.getNiceName() ?: 'Component'}
        </g:else>
      </span>
    </div>
    <g:if test="${displayobj}">
      <ul class="nav navbar-nav navbar-right">
        <g:if test="${org.gokb.cred.KBComponent.isAssignableFrom(displayobj.class)}">
          <li><a onClick="javascript:toggleWatch('${displayobj.class.name}:${displayobj.id}')"
                id="watchToggleLink"
                title="${user_watching ? 'You are watching this item' : 'You are not watching this item'}"
                style="cursor:pointer;"
                  ><i id="watchIcon" class="glyphicon ${user_watching ? 'glyphicon-eye-open' : 'glyphicon-eye-close'}"></i> <span id="watchCounter" class="badge badge-warning"> ${num_watch}</span></a></li>
        </g:if>
        <li><a data-toggle="modal" data-cache="false"
              title="Show History"
              data-remote='<g:createLink controller="fwk" action="history" id="${displayobj.class.name}:${displayobj.id}"/>'
              data-target="#modal"><i class="glyphicon glyphicon-time"></i></a></li>

        <li><a data-toggle="modal" data-cache="false"
              title="Show Notes"
              data-remote='<g:createLink controller="fwk" action="notes" id="${displayobj.class.name}:${displayobj.id}"/>'
              data-target="#modal"><i class="glyphicon glyphicon-comment"></i>
                <span class="badge badge-warning"> ${num_notes}</span>
            </a></li>
      </ul>
    </g:if>
  </div>
</nav>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-body">
      <g:if test="${displayobj != null}">
        <g:if test="${ (!displayobj.respondsTo("isEditable")) || displayobj.isEditable() }" >
          <g:if test="${ !((request.curator != null ? request.curator.size() > 0 : true) || (params.curationOverride == "true")) }" >
            <div class="col-xs-3 pull-right well">
              <div class="alert alert-warning">
                <h4>Warning</h4>
                <p>You are not a curator of this component. You can still edit it, but please contact a curator before making major changes.</p>
                <p><g:link class="btn btn-danger" controller="${ params.controller }" action="${ params.action }" id="${ displayobj.className }:${ displayobj.id }" params="${ (request.param ?: [:]) + ["curationOverride" : true] }" >Confirm and switch to edit mode</g:link></p>
              </div>
            </div>
          </g:if>
          <g:elseif test="${displayobj.respondsTo('availableActions')}">
            <div class="col-xs-3 pull-right well" id="actionControls">
              <g:form controller="workflow" action="action" method="post" class='action-form'>
                <h4>Available actions</h4>
                <input type="hidden"
                       name="bulk:${displayobj.class.name}:${displayobj.id}"
                       value="true" />
                <div class="input-group">
                  <select id="selectedAction" name="selectedBulkAction" class="form-control" >
                    <option value="">-- Select an action to perform --</option>
                    <g:each var="action" in="${displayobj.availableActions()}">
                      <option value="${action.code}">
                        ${action.label}
                      </option>
                    </g:each>
                  </select>
                  <span class="input-group-btn">
                    <button type="submit" class="btn btn-default" >Go</button>
                  </span>
                </div>
              </g:form>
            </div>
          </g:elseif>
        </g:if>
        <g:if test="${displaytemplate != null}">
          <g:if test="${displaytemplate.type=='staticgsp'}">
            <g:render template="${displaytemplate.rendername}"
                      contextPath="../apptemplates"
                      model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}" />
          </g:if>
        </g:if>
      </g:if>
      <g:else>
        <div class="alert alert-danger" style="display:inline-block;">Unable to find record in database : Please verify your input or notify support</div>
      </g:else>
    </div>
  </div>

  <div id="modal" class="qmodal modal fade modal-wide" role="dialog">
    <div class="modal-dialog">
      <div class="modal-content">
        <div class="modal-header">
          <button type="button" class="close" data-dismiss="modal" aria-hidden="true">&times;</button>
          <h3 class="modal-title">Loading Content..</h3>
        </div>
        <div class="modal-body"></div>
          <div class="modal-footer">
            <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
          </div>
        </div>
      </div>
    </div>

   <asset:script type="text/javascript" >
        $(document).ready(function(){
          var loading_spinner =
          $("#modal").on('show.bs.modal', function(){
            $(".modal-content").empty();
            $(".modal-content").append('<div class="modal-loading"><h4>Loading <asset:image src="img/loading.gif" /></h4></div>');
          });
        });
   
        function toggleWatch(oid) {
          $.ajax({
            url: '/gokb/fwk/toggleWatch?oid='+oid,
            dataType:"json"
          }).done(function(data) {
            var counter = parseInt($('#watchCounter').html());
            if ( data.change == '-1' ) {
              $('#watchToggleLink').prop('title','You are not watching this resource');
              $('#watchIcon').removeClass('glyphicon-eye-open');
              $('#watchIcon').addClass('glyphicon-eye-close');
              $('#watchCounter').html(counter-1);
            }
            else {
              $('#watchToggleLink').prop('title','You are watching this resource');
              $('#watchIcon').removeClass('glyphicon-eye-close');
              $('#watchIcon').addClass('glyphicon-eye-open');
              $('#watchCounter').html(counter+1);
            }
          });
        }

   </asset:script>
</body>
</html>
