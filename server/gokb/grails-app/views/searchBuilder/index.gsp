<r:require modules="gokbstyle"/>
<r:require modules="editable"/>


<!DOCTYPE html>
<html>
  <head>
    <meta name="layout" content="main"/>

    <r:require modules="gokbstyle"/>
    <r:require module="jquery-ui"/>
    <r:require modules="dynatree"/>

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
              <dd><input type="hidden" name="cgfClassName" class="cfgClassName input-xxlarge" onChange="javascript:updateSelClass(this.value);"/></dd>
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
                    <div id="QBEDomainTree" class="domainModelTree"></div>
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
                    <div id="ResultsDomainTree" class="domainModelTree"></div>
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

        $("#QBEDomainTree").dynatree({
          onActivate: function(node) {
            // A DynaTreeNode object is passed to the activation handler
            // Note: we also get this event, if persistence is on, and the page is reloaded.
            alert("You activated " + node.data.title);
          },
          children: [
          //   {title: "Item 1"},
          //   {title: "Folder 2", isFolder: true, key: "folder2",
          //     children: [
          //       {title: "Sub-item 2.1"},
          //       {title: "Sub-item 2.2"}
          //     ]
          //   },
          //   {title: "Item 3"}
          ],
          onLazyRead: function(node){
            console.log("onLazyRead...");
            node.appendAjax({
              url: "<g:createLink controller='searchBuilder' action='getClassProperties'/>",
              data: {key: node.data.key,
                mode: "funnyMode"
              }
            });
          }
        });
      });

      function updateSelClass(classname) {
        console.log("updateSelClass..."+classname);

        var rootNode = $("#QBEDomainTree").dynatree("getRoot");

        rootNode.removeChildren();

        // Call the DynaTreeNode.addChild() member function and pass options for the new node
        var childNode = rootNode.addChild({
            title: classname,
            tooltip: "Query builder for "+classname,
            isFolder: true,
            isLazy: true
        });

        // this adds a child node....
        // childNode.addChild({
        //     title: "Document using a custom icon",
        //     icon: "customdoc1.gif"
        // });

      }
    </script>


  </body>
</html>
