
<div id="tab-decision-suppport">
<div class="well clearfix">
  <span class="pull-right">
    <g:form name="rsform" controller="resource" action="show" id="${params.id}" fragment="ds" method="post">
      Filter DS Lines:&nbsp;
      <input name="DSFilter" value="curator" type="radio" onClick="$('#rsform').submit();" ${params.DSFilter=='curator'?'checked':''}>Curators</input>&nbsp;|&nbsp;
      <input name="DSFilter" value="mylib" type="radio" onClick="$('#rsform').submit();" ${params.DSFilter=='mylib'?'checked':''}>My Library</input>&nbsp;|&nbsp;
      <input name="DSFilter" value="otherlib" type="radio" onClick="$('#rsform').submit();" ${params.DSFilter=='otherlib'?'checked':''}>Other Libraries</input>&nbsp;|&nbsp;
      <input name="DSFilter" value="vendor" type="radio" onClick="$('#rsform').submit();" ${params.DSFilter=='vendor'?'checked':''}>Vendors</input>&nbsp;|&nbsp;
      <input name="DSFilter" value="all" type="radio" onClick="$('#rsform').submit();" ${params.DSFilter==''?'checked':''}>All</input>&nbsp;&nbsp;<br/>
    </g:form>
  </span>
</div>

<div class="panel-group" id="accordion" role="tablist" aria-multiselectable="true">

  <g:each in="${d.getDecisionSupportLines(params.DSFilter)?.values()}" var="dsl">
 
    <div class="panel panel-default">

      <div class="panel-heading" role="tab" id="${dsl.id}">
        <h4 class="panel-title">
          <a role="button" class="collapsed" data-toggle="collapse" data-parent="#accordion" href="#collapse${dsl.id}" aria-expanded="true" aria-controls="collapse${dsl.id}">
            ${dsl.description} - ${dsl.comment_count} comments, ${dsl.vote_count} votes
          </a>
        </h4>
      </div>

      <div id="collapse${dsl.id}" class="panel-collapse collapse collapsed" role="tabpanel" aria-labelledby="${dsl.id}">
        <div class="panel-body">

          <div class="container-fluid">

            <g:each in="${dsl.criterion}" var="id, c" status="i">
              <div class="row well">
                <div class="col-md-4">
                    <div class="criterionTitle"><strong>${c.description?:c.title}</strong> - ${c.explanation?:'No explanation available'}
                    </div></br>

                    <div class="vote" style="white-space: nowrap;">
                        <g:if test="${c['yourVote'].isEmpty()}">
                            <i id="${c['appliedTo']}_${id}_q_neutral" class="text-neutral"> <span class="fa fa-question-circle fa-2x"></span><span>&nbsp;&nbsp;Your Rating:</span></i></br></br>
                        </g:if>
                        <g:elseif test="${c['yourVote'][0] == 'Unknown'}">
                            <span><b>You have commented without rating, please rate!</b></span>
                        </g:elseif>
                        <g:else>
                            <span><b>Your Rating </b></span>
                        </g:else>
                        <div class="DSVote" id="currentVote${c['appliedTo']}_${id}">
                            <a id="${c['appliedTo']}_${id}_r_negative" title="${c['voteCounter'][0] +  (c['voteCounter'][3]>0? ' Red vote(s) and ' + c['voteCounter'][3] + ' commented only':' Red vote(s)') }" href='#' ${c['yourVote'][0]=='Red'?'class="text-negative selected"':''} ><i class="fa fa-times-circle fa-2x"></i></a> &nbsp;
                            <a id="${c['appliedTo']}_${id}_a_contentious" title="${c['voteCounter'][1] +  (c['voteCounter'][3]>0? ' Amber vote(s) and ' + c['voteCounter'][3] + ' commented only':' Amber vote(s)') }" href='#' ${c['yourVote'][0]=='Amber'?'class="text-contentious selected"':''} ><i class="fa fa-question-circle fa-2x"></i></a>&nbsp;
                            <a id="${c['appliedTo']}_${id}_g_positive" title="${c['voteCounter'][2] +  (c['voteCounter'][3]>0? ' Green vote(s) and ' + c['voteCounter'][3] + ' commented only':' Green vote(s)') }" href='#' ${c['yourVote'][0]=='Green'?'class="text-positive selected"':''} ><i class="fa fa-check-circle fa-2x"></i></a>
                        </div>
                    </br></br>
                        <g:if test="${c['otherVotes'].isEmpty()}">
                            No one else has rated yet
                        </g:if>
                        <g:elseif test="${grailsApplication.config.feature.otherVoters}">
                            <table id="otherVoters" style="margin: 0; padding: 0">
                                <thead>
                                <tr>
                                    <th>Other Ratings</th>
                                </tr>
                                </thead>
                                <tbody>
                                <g:each in="${c['otherVotes']}" var="o">
                                    <tr>
                                        <td>
                                            <p class="DSAuthor DSInlineBlock" title="${o[2]?.org?.name}">
                                                ${o[2]?.displayName}
                                            </p>
                                        </td>
                                        <td>
                                            <p class="DSVote DSInlineBlock">
                                                <span id="${c['appliedTo']}_${id}_r_negative" ${o[0]=='Red'?'class="text-negative"':''} ><i class="fa fa-times-circle fa-2x"></i></span> &nbsp;
                                                <span id="${c['appliedTo']}_${id}_a_contentious"  ${o[0]=='Amber'?'class="text-contentious"':''} ><i class="fa fa-question-circle fa-2x"></i></span>&nbsp;
                                                <span id="${c['appliedTo']}_${id}_g_positive" ${o[0]=='Green'?'class="text-positive"':''} ><i class="fa fa-check-circle fa-2x"></i></span>
                                                <g:if test="${o[0]=='Unknown'}"><i>(Commented only)</i></g:if>
                                            </p>
                                        </td>
                                    </tr>
                                </g:each>
                                </tbody>
                            </table>
                        </g:elseif>
                        <g:else>
                            <div id="otherVoters">
                                <table>
                                    <thead>
                                    <tr>
                                        <th colspan="3">Other Ratings</th>
                                    </tr>
                                    </thead>
                                    <tbody>
                                    <tr>
                                        <td><a id="otherRed"  href='#' class="text-negative"><i class="fa fa-times-circle fa-2x"></i></a> &nbsp;</td>
                                        <td><a id="OtherYellow"  href='#' class="text-contentious" ><i class="fa fa-question-circle fa-2x"></i></a>&nbsp;</td>
                                        <td><a id="OtherGreen"  href='#' class="text-positive"><i class="fa fa-check-circle fa-2x"></i></a></td>
                                    </tr>
                                    <tr>
                                        <td><span class="badge">${c['voteCounter'][0]}</span></td>
                                        <td><span class="badge">${c['voteCounter'][1]}</span></td>
                                        <td><span class="badge">${c['voteCounter'][2]}</span></td>
                                    </tr>
                                    </tbody>
                                </table>
                            </div>
                        </g:else>
                    </div>
                </div>
                <div class="col-md-8">

                    <dl>
                        <div class="panel panel-default">
                          <!-- Default panel contents -->
                          <div class="panel-heading">Comments</div>
                          <!-- List group -->
                          <ul class="list-group" id="${c['appliedTo']}_${id}_notestable"> 
                            <g:each in="${c['notes']}" var="note">
                              <li class="list-group-item">
                                <g:if test="${!note.isDeleted && note.criterion.user.id == user.id}" >
                                    <g:xEditable owner="${note}" field="note"/>
                                </g:if>
                                <g:else>
                                    ${note.note}
                                </g:else>
                                <hr/>
                                <div class="pull-right">
                                <a data-comp="${c['appliedTo']}_${id}" data-note="${note.id}" class="noteDelete text-negative fa fa-times-circle"></a>
                                <span onClick="javascript:meToo('org.gokb.cred.DSNote:${note.id}','#count_for_comment_${note.id}');"><i id="thumb_for_comment_${note.id}" class="fa fa-thumbs-up notethumb"></i></span>
                                <span id="count_for_comment_${note.id}" class="badge">${note.getLikeCount()}</span>
                                </div>
                                <div>
                                  by <strong>${note?.criterion?.user.username}</strong> of
                                   <g:if test="${note.criterion?.user?.org}">
                                      <g:if test="${note.criterion?.user?.org?.name == null}">
                                          N/A
                                      </g:if>
                                      <g:else>
                                          <strong>${note.criterion?.user?.org?.name} (${note.criterion?.user?.org?.mission?.value})</strong>
                                      </g:else>
                                    </g:if>
                                    <g:else>
                                      <g:link controller="home" action="profile">No user org</g:link>
                                    </g:else>
                                   on <strong><g:if test="${note.lastUpdated == note.dateCreated}"><g:formatDate date="${note.dateCreated}" /></g:if>
                                    <g:else>Edited: <g:formatDate date="${note.lastUpdated}" /></g:else></strong>

                                  
                                </div>
                              </li>
                            </g:each>
                            <li class="list-group-item">
                              <form role="form" class="form" onsubmit='return addNote("${c['appliedTo']}_${id}", "${user.username}", "${user?.org?.name}")'>
                                <div class="form-group">
                                  <div class="input-group">
                                    <span class="input-group-addon">Add note</span>
                                    <textarea class="form-control" id="${c['appliedTo']}_${id}_newnote"></textarea>
                                    <span class="input-group-addon">
                                      <button type="submit">Add</button>
                                    </span>
                                  </div>
                                </div>
                              </form>
                            </li>
                          </ul>
                        </div>
                    </dl>

                </div>
              </div>
            </g:each>
          </div>
        </div>
      </div>
    </div>

  </g:each>
</div>

</div>

<asset:script type="text/javascript">
  var hash = window.location.hash;
  hash && $('ul.nav a[href="' + hash + '"]').tab('show');
</asset:script>

