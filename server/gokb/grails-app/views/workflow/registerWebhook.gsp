<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <r:require modules="editable"/>
    <title>GOKb::Package - Register Webhook </title>
  </head>
  <body>
    <div class="container-fluid">
      <g:form controller="workflow" action="processTitleChange" method="get">
        <div class="row-fluid">
          <div class="span12 hero well">
            Register Webhook
          </div>
        </div>
        <div class="row-fluid">
  
          <div class="span12">
            <img class="pull-right" src="${resource(dir: 'images', file: 'WebHook.png')}"/>
            
            Register WebHook callbacks for:<br/>
            <g:each in="${objects_to_action}" var="o">
              <input type="checkbox" name="tt:${o.id}" checked="true"/> ${o.name}<br/>
            </g:each>
            <hr>

            <h3>Link to Existing hook</h3>
            <g:form action="processCreateWebHook">
              <dl class="dl-horizontal">
                <dt>Url</dt>
                <dd><g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="existingHook" baseClass="org.gokb.cred.WebHookEndpoint" filter1="${request.user?.id}"/></dd>
              </dl>
            </g:form>

            <hr>

            <h3>Link to New hook</h3>
            <g:form action="processCreateWebHook">
              <dl class="dl-horizontal">
                <dt>Hook Name</dt> <dd><input type="text" name="newHookName"/></dd>
                <dt>Url</dt> <dd><input type="text" name="newHookUrl"/></dd>
                <dt>Auth</dt> <dd><select name="newHookAuth">
                                    <option value="0">Anonymous (No Auth)</option>
                                    <option value="1">HTTP(s) Basic</option>
                                    <option value="2">Signed HTTP Requests</option>
                                  </select></dd>
                <dt>Principal</dt> <dd><input type="text" name="newHookPrin"/></dd>
                <dt>Credentials</dt> <dd><input type="text" name="newHookCred"/></dd>
              </dl>
            </g:form>

          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>

