<!DOCTYPE html>
<html>
<head>
<meta name='layout' content='public' />
<title>GOKb: Packages</title>
</head>

<body>

   <nav class="navbar navbar-default" id="primary-nav-bar" role="navigation">
     <div class="container">
       <!-- Brand and toggle get grouped for better mobile display -->
       <div class="navbar-header">
         <button type="button" class="navbar-toggle" data-toggle="collapse" data-target="#primary-nav">
           <span class="sr-only">Toggle navigation</span>
           <span class="icon-bar"></span>
           <span class="icon-bar"></span>
           <span class="icon-bar"></span>
         </button>
         <!-- navbar-brand is hidden on larger screens, but visible when the menu is collapsed -->
         <a class="navbar-brand" href="index.html">GOKb</a>
       </div>

       <div class="collapse navbar-collapse" id="primary-nav">
         <ul class="nav navbar-nav">
           <li class="hidden active"><a href="#page-body"></a></li>
           <li><span>Package Content :: </span></li>
         </ul>
       </div>

      </div> <!-- /.container -->
    </nav>

  <div class="container">

    <div class="row"> <div class="box">
   
     <div class="col-lg-12 ">
      <div class="well">
      <g:if test="${pkgName}">

        <h1>${pkgName}</h1>

        <table class="table table-striped table-bordered">
          <thead>
            <tr>
              <th>Title</th>
              <th>Identifiers</th>
              <th>Coverage</th>
            </tr>
          </thead>
          <tbody>
            <g:each in="${tipps}" var="t">
              <tr>
                <td>${t.title.name}</th>
                <td></th>
                <td></th>
              </tr>
            </g:each>
          </tbody>
        </table>
      </g:if>

      </div>
      </div>
    </div></div>

  </div> <!-- /.container -->
</body>
</html>
