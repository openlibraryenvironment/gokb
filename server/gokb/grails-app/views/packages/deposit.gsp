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

      </div> <!-- /.container -->
    </nav>

  <div class="container well">

    <g:if test="${message}">
      <div class="well">${message}</div>
    </g:if>

    <div class="row">
      <h1>Package Deposit</h1>
      <g:form controller="packages" action="deposit">
        Deposit file: <input type="file" name="content"/>
        <button type="submit">Submit</button>
      </g:form>
    </div>

  </div> <!-- /.container -->
</body>
</html>
