<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <r:require modules="editable"/>
    <title>GOKb::Package Replacement</title>
  </head>
  <body>
    <div class="container-fluid">
      <g:form controller="workflow" action="processPackageReplacement" method="get">
        <div class="row-fluid">
  
          <div class="span6">
            Update TIPP records and replace the following platform(s)<br/>
              <g:each in="${objects_to_action}" var="o">
                <input type="checkbox" name="tt:${o.id}" checked="true"/> ${o.name}<br/>
              </g:each>
              </ul>
          </div>

          <div class="span6">
            With Platform: <g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="newplatform" baseClass="org.gokb.cred.Platform"/><br/>
            &nbsp;<br/>
            <input type="submit" value="Update" class="btn btn-primary btn-small"/>
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>

