<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: ${displayobj?.getNiceName() ?: 'Component'}
<g:if test="${displayobj}">
&lt;${ displayobj?.isEditable() ? 'Editable' : ( response.status == 403 ? 'Not Accessible' : 'Read Only') }&gt;
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
        <g:if test="${displayobj?.id != null}">
          ${displayobj?.getNiceName() ?: 'Component'} : ${displayobj?.id}
          <g:if test="${response.status != 403}">
            <g:if test="${ displayobj?.respondsTo('getDisplayName') && displayobj.getDisplayName()}"> - <strong>${displayobj.getDisplayName()}</strong></g:if>
            <g:if test="${ !displayobj?.isEditable() }"> <small><i>&lt;Read only&gt;</i></small> </g:if>
          </g:if>
          <g:else>
            <small><i>&lt;Not Accessible&gt;</i></small>
          </g:else>
        </g:if>
        <g:elseif test="${displayobj}">
          Create New ${displayobj?.getNiceName() ?: 'Component'}
        </g:elseif>
      </span>
    </div>
    <g:if test="${displayobj && response.status != 403}">
      <ul class="nav navbar-nav navbar-right">
        <g:if test="${org.gokb.cred.KBComponent.isAssignableFrom(displayobj.class)}">
          <li><a onClick="javascript:toggleWatch('${displayobj.class.name}:${displayobj.id}')"
                id="watchToggleLink"
                title="${user_watching ? 'You are watching this item' : 'You are not watching this item'}"
                style="cursor:pointer;">
                <i id="watchIcon" class="fa ${user_watching ? 'fa-eye' : 'fa-eye-slash'}"></i> <span id="watchCounter" class="badge badge-warning"> ${num_watch}</span></a></li>
          <li><a data-toggle="modal" data-cache="false"
                title="Show History (with Combos)"
                data-remote='<g:createLink controller="fwk" action="history" id="${displayobj.class.name}:${displayobj.id}" params="[withCombos:true]"/>'
                data-target="#modal"><i class="fas fa-history"></i></a></li>
        </g:if>
        <li><a data-toggle="modal" data-cache="false"
              title="Show Notes"
              data-remote='<g:createLink controller="fwk" action="notes" id="${displayobj.class.name}:${displayobj.id}"/>'
              data-target="#modal"><i class="fa fa-comments"></i>
                <span class="badge badge-warning"> ${num_notes}</span>
            </a></li>
      </ul>
    </g:if>
  </div>
</nav>
  <div id="mainarea" class="panel panel-default">
    <div class="panel-body">
      <g:if test="${response.status == 403}">
        <g:message code="springSecurity.denied.message" />
      </g:if>
      <g:elseif test="${displayobj != null}">
        <g:if test="${ !((request.curator != null ? request.curator.size() > 0 : true) || (params.curationOverride == "true" && request.user.isAdmin())) }" >
          <div class="col-xs-3 pull-right well" style="min-width:320px;">
            <div class="alert alert-info" style="font-weight:bold;">
              <h4>Info</h4>
              <p>You are not a curator of this component. If you notice any errors, please contact a curator or request a review.</p>
            </div>
            <sec:ifAnyGranted roles="ROLE_ADMIN">
              <div class="alert alert-warning" style="font-weight:bold;">
                <h4>Warning</h4>
                <p>As an admin you can still edit, but please contact a curator before making major changes.</p>
                <p>
                  <g:link class="btn btn-danger"
                          controller="${ params.controller }"
                          action="${ params.action }"
                          id="${ displayobj.className }:${ displayobj.id }"
                          params="${ (request.param ?: [:]) + ["curationOverride" : true] }" >
                    Enable admin override
                  </g:link>
                </p>
              </div>
              <g:if test="${displayobj.respondsTo('getCuratoryGroups')}">
                <div>
                  <h4>Curatory Groups</h4>
                  <div style="background-color:#ffffff">
                    <g:render template="/apptemplates/curatory_groups" model="${[d:displayobj, editable:false]}" />
                  </div>
                </div>
              </g:if>
            </sec:ifAnyGranted>
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
                  <g:each var="action" in="${displayobj.userAvailableActions()}">
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
            <g:if test="${displayobj.respondsTo('getCuratoryGroups')}">
              <div>
                <h4>Curatory Groups</h4>
                <div style="background-color:#ffffff">
                  <g:render template="/apptemplates/curatory_groups" model="${[d:displayobj]}" />
                </div>
              </div>
            </g:if>
          </div>
        </g:elseif>
        <g:elseif test="${displayobj.respondsTo('getCuratoryGroups')}">
          <div class="col-xs-3 pull-right well" id="actionControls">
            <div>
              <h4>Curatory Groups</h4>
              <div style="background-color:#ffffff">
                <g:render template="/apptemplates/curatory_groups" model="${[d:displayobj]}" />
              </div>
            </div>
          </div>
        </g:elseif>
        <g:if test="${displaytemplate != null}">
          <!-- Using display template ${displaytemplate.rendername} -->
          <g:if test="${displaytemplate.type=='staticgsp'}">
            <g:render template="/apptemplates/${displaytemplate.rendername}"
                      model="${[d:displayobj, rd:refdata_properties, dtype:displayobjclassname_short]}" />
          </g:if>
        </g:if>
      </g:elseif>
      <g:else>
        <div class="alert alert-danger" style="display:inline-block;font-weight:bolder;"><g:message code="component.notFound.label" args="[params.id]"/></div>
      </g:else>
    </div>
  </div>

  <g:render template="/apptemplates/messages"
          model="${ ["preMessage" : preMsg ]}" />

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

        $(document).on('show.bs.modal','#modal', function(){
          $(".modal-content").empty();
          $(".modal-content").append('<div class="modal-loading"><h4>Loading <asset:image src="img/loading.gif" /></h4></div>');
        });

        $(document).ready(function(){

          $('a.editable').on('click', function(){
            var editable = $(this);

            var select = editable.next();

            var submit = select.find('.editable-submit');

            submit.on('click', function(){

              var follow_link = select.next();
              var related_editable = select.prev();

              window.setTimeout(function() {

                var new_linked_oid = null;

                editable.each(function() {
                  var new_tid = false;

                  $.each(this.attributes, function(){
                    if(this.specified && this.name == 'target-id') {
                      new_linked_oid = this.value;
                      new_tid = true;
                    }
                  })

                  if(!new_tid) {
                    new_linked_oid = null;
                  }
                })

                if (new_linked_oid) {
                  if (follow_link.attr('href')) {
                    var old_href = follow_link.attr('href');
                    var truncated_link = old_href.substring(0, old_href.lastIndexOf('/') + 1);

                    follow_link.attr('href', truncated_link + new_linked_oid);
                  }else {
                    var new_url = contextPath + "/resource/show/" + new_linked_oid;
                    related_editable.after(' &nbsp; <a href="' + new_url + '"><i class="fas fa-eye"></i></a>');
                  }
                } else if (follow_link.attr('href')) {
                  $(follow_link).remove();
                }
              }, 500);
            });
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
              $('#watchIcon').removeClass('fas fa-eye');
              $('#watchIcon').addClass('far fa-eye-slash');
              $('#watchCounter').html(counter-1);
            }
            else {
              $('#watchToggleLink').prop('title','You are watching this resource');
              $('#watchIcon').removeClass('far fa-eye-slash');
              $('#watchIcon').addClass('fas fa-eye');
              $('#watchCounter').html(counter+1);
            }
          });
        }

        var hash = window.location.hash;
        hash && $('ul.nav a[href="' + hash + '"]').tab('show');

        $('.nav-tabs > li > a').not('.disabled').click(function (e) {
          $(this).tab('show');
          var scrollmem = $('body').scrollTop();
          console.log("scrollTop");
          window.location.hash = this.hash;
          $('html,body').scrollTop(scrollmem);
        });

   </asset:script>
</body>
</html>
