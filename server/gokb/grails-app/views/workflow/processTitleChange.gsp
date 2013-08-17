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
      <g:form controller="workflow" action="performTitleChange">
        <div class="row-fluid">
          <div class="span12 hero well">
            Title Transfer (2/2)
          </div>
        </div>
        <div class="row-fluid">
  
          <div class="span12">

            The following titles:
            <ul>
              <g:each in="${titles}" var="title">
                <li>${title.name}</li>
              </g:each>
            </ul>

            Will be transferred from their current publisher to ${newPublisher.name}. The following TIPPs are currently associated with these titles
            and need to be migrated.

            <table class="table table-striped">
              <thead>
                <tr>
                  <th>Select</th>
                  <th>Current Title</th>
                  <th>Current Package</th>
                  <th>Current Platform</th>
                  <th>Close Current TIPP?</th><th>New TIPPS</th></tr>
              </thead>
              <tbody>
                <g:each in="${tipps}" var="tipp">
                  <tr>
                    <td><input type="checkbox" checked="true"/></td>
                    <td>${tipp.title.name}</td><td>${tipp.pkg.name}</td><td>${tipp.hostPlatform.name}</td>
                    <td><input type="checkbox" checked="true"/></td><td>bbbb</td></tr>
                </g:each>
              </tbody>
            </table>

            Use the following form to indicate the package and platform for new TIPPs. Select/Deselect TIPPS above to indicate

            <dl class="dl-horizontal">
              <div class="control-group">
                <dt>New Package</dt>
                <dd><g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="Package" baseClass="org.gokb.cred.Package"/></dd>
              </div>

              <div class="control-group">
                <dt>New Platform</dt>
                <dd><g:simpleReferenceTypedown class="input-xxlarge" style="width:350px;" name="Platform" baseClass="org.gokb.cred.Platform"/></dd>
              </div>
            </dl>


            
 
            <br/>
            <input type="submit" value="Process" class="btn btn-primary btn-small"/>


          </div>

 
        </div>
      </g:form>
    </div>
  </body>
</html>

