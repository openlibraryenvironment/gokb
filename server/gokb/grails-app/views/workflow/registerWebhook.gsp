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
  
          <div class="span6">
            <img class="pull-right" src="${resource(dir: 'images', file: 'favicon.ico')}"/>
            
            Register WebHook callbacks for:<br/>
              <g:each in="${objects_to_action}" var="o">
                <input type="checkbox" name="tt:${o.id}" checked="true"/> ${o.name}<br/>
              </g:each>
              </ul>
          </div>

          <div class="span6">
            Webhook form
            URL
            Auth
            Principal
            Credentials
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>

