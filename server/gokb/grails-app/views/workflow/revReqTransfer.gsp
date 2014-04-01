<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <r:require modules="editable"/>
    <title>GOKb::Review Request Transfer</title>
  </head>
  <body>
    <div class="container-fluid">
      <g:form controller="workflow" action="processRRTransfer" method="get">
        <input type="hidden" name="from" value="${request.getHeader('referer')}"/>
        <div class="row-fluid">
          <div class="span12 hero well">
            Review Request Transfer
          </div>
        </div>
        <div class="row-fluid">
  
          <div class="span6">
            Transfer the following Review Requests:<br/>
              <g:each in="${objects_to_action}" var="o">
                <input type="checkbox" name="tt:${o.id}" checked="true"/> ${o.status} ${o.stdDesc?.value} ${o.reviewRequest} ${o.descriptionOfCause}<br/>
              </g:each>
              </ul>
          </div>

          <div class="span6">
            To User: <g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="allocToUser" baseClass="org.gokb.cred.User"/><br/>
            Note: <textarea rows="5" cols="40" name="note"></textarea>
            &nbsp;<br/>
            <input type="submit" value="Transfer ->" class="btn btn-primary btn-small"/>
          </div>

 
        </div>
        <div class="row-fluid">
          <div class="span12">
            Notes
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>

