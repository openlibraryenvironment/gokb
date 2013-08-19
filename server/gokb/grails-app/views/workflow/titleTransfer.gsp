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
        <div class="row-fluid">
          <div class="span12 hero well">
            Title Transfer (1/2)
          </div>
        </div>
        <div class="row-fluid">
  
          <div class="span6">
            Title Transfer the following:<br/>
              <g:each in="${objects_to_action}" var="o">
                <input type="checkbox" name="tt:${o.id}" checked="true"/> ${o.name} (Currently : o.currentPublisher)<br/>
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
            This action will update the publisher details on all selected titles. Any current publisher will have an end date set on it's
            relationship with a publisher and a new publisher will be created. TIPPs relating to this title are not currently changed, a number of
            options are available <ul><li>Allow the admin to select a package on this screen and update all tipps to point to that package</li><li>Flag
            all affected tipps for review</li><li>Proceed to a third screen listing all tipps and asking the user to update each one</li></ul> The major
            issue to resolve here is that a title appears in many places, each of which may require individual decisions and attention, therefore
            bulk updating tipps might be problematic.
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>

