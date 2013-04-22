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
      <div class="row-fluid">
        <div class="span12 hero well">
          Title Transfer
        </div>
      </div>
      <div class="row-fluid">
        <div class="span6">
          Title Transfer the following:
          <ul>
            <g:each in="${objects_to_action}" var="o">
              <li>
                ${o.name} (Currently : lookup current pub or NONE)
              </li>
            </g:each>
          </ul>
        </div>
        <div class="span6">
          New Publisher: <g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="title" baseClass="org.gokb.cred.Org"/><br/>
        </div>
    </div>
  </body>
</html>

