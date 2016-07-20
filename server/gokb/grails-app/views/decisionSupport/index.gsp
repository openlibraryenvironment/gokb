<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <title>GOKb: Decision Support Browser</title>
  </head>
  <body>
    <h1 class="page-header">Decision Support Browser</h1>
    <div class="container-fluid">
      <g:form controller="decisionSupport">
        <div class="row">
          <div class="col-md-12 centered">
            Browse&nbsp;:&nbsp;<select name="dimension">
              <option value="Platform">By Platform</option>
              <option value="Package">By Package</option>
              <option value="Title">By Title</option>
            </select>
          </div>
        </div>
        <div class="row">
  
          <div class="col-md-12">
            <table class="table table-striped">
              <thead>
              </thead>
              <tbody>
                <g:each in="${matrix}" var="r">
                  <tr>
                    <td>${r}</td>
                  </tr>
                </g:each>
              </tbody>
            </table>
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>
