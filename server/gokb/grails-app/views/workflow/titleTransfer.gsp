<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <r:require modules="editable"/>
    <title>GOKb::Title Transfer</title>
  </head>
  <body>
    <div class="container-fluid">
      <g:form controller="workflow" action="processTitleChange" method="get">
        <input type="hidden" name="from" value="${request.getHeader('referer')}"/>
        <div class="row-fluid">
          <div class="span12 hero well">
            Title Transfer (1/2)
          </div>
        </div>
        <div class="row-fluid">
  
          <div class="span6">
            Title Transfer the following:<br/>
              <g:each in="${objects_to_action}" var="o">
                <input type="checkbox" name="tt:${o.id}" checked="true"/> ${o.name} (Currently : ${o.currentPublisher?.name})<br/>
              </g:each>
              </ul>
          </div>

          <div class="span6">
            New Publisher: <g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="title" baseClass="org.gokb.cred.Org"/><br/>
            &nbsp;<br/>
            <input type="submit" value="Step 2" class="btn btn-primary btn-small"/>
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

