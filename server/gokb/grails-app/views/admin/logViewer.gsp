<!DOCTYPE html>
<html>
<head>
<meta name="layout" content="sb-admin" />
<title>GOKb: Log Watcher</title>
<asset:script type="text/javascript" >
  var initScroll = false;
  (function($){
    $(document).ready(function(){
      var availableSpace = $( window ).innerHeight();
      availableSpace = availableSpace - $("h1.page-header").outerHeight(true);
      availableSpace = availableSpace - $('#page-wrapper').siblings('.navbar').outerHeight(true);
      $('#log-wrapper')
        .css('height', (availableSpace -10) + 'px');
        
      // Listen to the ajax event.
      $(document).on("ajaxStop", function () {
        if (!initScroll) {
	        var height = $('#log-wrapper')[0].scrollHeight;
	        $('#log-wrapper').scrollTop(height);
	        initScroll = true;
	      }
      });
    });
  
  })(jQuery);
</asset:script>
</head>
<body>
  <h1 class="page-header">Log Viewer</h1>
  <div id="log-wrapper">
    <g:link class="display-inline" controller="file" params="[filePath: (file)]" data-auto-refresh="1000" data-content-selector=".fileContents" />
  </div>
</body>
</html>
