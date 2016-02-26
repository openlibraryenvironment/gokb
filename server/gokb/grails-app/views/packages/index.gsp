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
           <li><span>GOKb Packages</span></li>
         </ul>
       </div>

       <div class="row">
         <div class="col-md-12">
           <g:form class="form" role="form" controller="packages" action="index" method="get">
             <span class="input-group">
               <input class="form-control input-large" type="text" name="q"/>
               <span class="input-group-btn">
                 <button class="btn btn-default" type="submit">Search</button>
               </span>
             </span>
           </g:form><br/>&nbsp;<br/>
         </div>
       </div>

       <div class="row">
         <div class="col-md-12">
          <table class="table table-striped">
            <thead>
              <tr>
                <th>Package name</th>
                <th>Provider</th>
                <th>Title count</th>
                <th>Last updated</th>
              </tr>
            </thead>
            <tbody>
              <g:each in="${hits}" var="hit">
                <tr>
                  <td><g:link controller="packages" action="packageContent" id="${hit.source._id}">${hit.source.name}</g:link></td>
                  <td></td>
                  <td></td>
                  <td></td>
                </tr>
              </g:each>
            </tbody>
          </table>
         </div>
       </div>


      </div> <!-- /.container -->
    </nav>

  <div class="container">

    <div class="row">
      Packages
    </div>

  </div> <!-- /.container -->
</body>
</html>
