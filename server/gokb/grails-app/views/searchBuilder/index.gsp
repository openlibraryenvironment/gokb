<r:require modules="gokbstyle"/>
<r:require modules="editable"/>


<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>
    <r:require modules="gokbstyle"/>
    <title>GOKbo : About</title>
  </head>
  <body>

    <div class="container-fluid">
      <div class="row-fluid">
        <div class="well span12">

          <div class="navbar">
            <div class="navbar-inner">
              <div class="brand">Edit Search Template</div>
            </div>
          </div>

    
          <dl class="dl-horizontal">

            <div class="control-group">
              <dt>Base Class</dt>
              <dd><input type="hidden" name="cgfClassName" class="cfgClassName input-xxlarge"/></dd>
            </div>
  
            <div class="control-group">
              <dt>Search Template Title</dt>
              <dd><input type="text" name="Template Name"/></dd>
            </div>
  
          </dl>

          <ul id="tabs" class="nav nav-tabs">
            <li class="active"><a href="#QBEForm" data-toggle="tab">QBE Form</a></li>
            <li><a href="#QResults" data-toggle="tab">Results</a></li>
          </ul>
          <div id="my-tab-content" class="tab-content">
            <div class="tab-pane active" id="QBEForm">
              <div class="container">
                <div class="row">
                  <div class="span6">
                    <h3>QBE Fields</h3>
                    <ul>
                    </ul>
                  </div>
                  <div class="span6">
                    <h3>Datamodel</h3>
                  </div>
                </div>
              </div>
            </div>

            <div class="tab-pane" id="QResults">
              <div class="container">
                <div class="row">
                  <div class="span6">
                    <h3>Result Columns</h3>
                    <ul>
                    </ul>
                  </div>
                  <div class="span6">
                    <h3>Datamodel</h3>
                  </div>
                </div>
              </div>
            </div>
          </div>

        </div>
      </div>
    </div>
  
    <script language="JavaScript">
      $(document).ready(function() {
        $(".cfgClassName").select2({
          placeholder: "Search for...",
          width:'resolve',
          minimumInputLength: 0,
          ajax: { // instead of writing the function to execute the request we use Select2's convenient helper
            url: "<g:createLink controller='searchBuilder' action='getClasses'/>",
            dataType: 'json',
            data: function (term, page) {
              return {
                format:'json'
              };
            },
            results: function (data, page) {
              // console.log("resultsFn");
              return {results: data.values};
            }
          }
        });
      });
    </script>


  </body>
</html>
