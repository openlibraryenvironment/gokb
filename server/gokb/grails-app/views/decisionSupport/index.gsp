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
                <tr>
                  <th rowspan="2"></th>
                  <g:each in="${matrix.criterion_heads}" var="ch">
                    <th colspan="${ch?.count}" style="background-color:${ch.color?:'none'};">${ch?.name}</th>
                  </g:each>
                </tr>
                <tr>
                  <g:each in="${matrix.criterion}" var="c">
                    <th style="background-color:${c.color?:'none'};">${c.title} ${c.description}</th>
                  </g:each>
                </tr>
              </thead>
              <tbody>
                <g:each in="${matrix.rowdata}" var="r">
                  <tr>
                    <td>${r.component.name}</td>
                    <g:each in="${r.data}" var="d">
                      <td>
                        ${d[1]}<br/>
                        ${d[2]}<br/>
                        ${d[3]}
                      </td>
                    </g:each>
                  </tr>
                </g:each>
              </tbody>
            </table>
          </div>
        </div>
      </g:form>
    </div>

    <pre>
      ${matrix}
    </pre>
  </body>
</html>
