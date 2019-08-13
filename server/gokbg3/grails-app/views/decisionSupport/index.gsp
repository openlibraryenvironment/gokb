<!DOCTYPE html>
<html>
  <head>
    <meta name='layout' content='sb-admin'/>
    <title>GOKb: Decision Support Dashboard</title>
  </head>
  <body>
    <h1 class="page-header">Decision Support Dashboard</h1>
    <div class="container-fluid">
      <g:form controller="decisionSupport" method="get">
        <div class="row">
          <div class="col-md-12 centered">
          </div>
        </div>
        <div class="row">
          <div class="col-md-12">
            <div class="table-responsive">
              <table class="table table-striped table-bordered">
                <thead>
                  <tr>
                    <th rowspan="2" style="min-width:250px;">
                      <g:form method="get" class="form-horizontal" controller="decisionSupport" role="form">
                        Browse&nbsp;:&nbsp;<select name="dimension" class="form-control">
                          <option value="Platform">By Platform</option>
                          <option value="Package">By Package</option>
                        </select><br/>
                        Filter:<input name="q" type="text" class="form-control" value="${params.q}"/>
                        <hr/>
                        <div class="btn-group pull-right" role="group" aria-label="Search Buttons">
                          <button name="searchAction" type="submit" class="btn btn-default" value="search">Apply</button>
                        </div>
                      </g:form>
                    </th>
                    <g:each in="${matrix.criterion_heads}" var="ch">
                      <th colspan="${ch?.count}" style="background-color:${ch.color?:'none'};"><g:link controller="resource" action="show" id="org.gokb.cred.DSCategory:${ch.id}">${ch?.name}</g:link></th>
                    </g:each>
                  </tr>
                  <tr>
                    <g:each in="${matrix.criterion}" var="c">
                      <th style="background-color:${c.color?:'none'};">
                        <g:link controller="resource" action="show" id="org.gokb.cred.DSCriterion:${c.id}" title="${c.explanation}">${c.description?:c.title}</g:link>
                        <input type="checkbox" name="show_category" value="${c.id}">
                      </th>
                    </g:each>
                  </tr>
                </thead>
                <tbody>
                  <g:each in="${matrix.rowdata}" var="r">
                    <tr>
                      <td><g:link controller="resource" action="show" id="${r.component.class.name}:${r.component.id}">${r.component.name}</g:link></td>
                      <g:each in="${r.data}" var="d">
                        <td style="white-space:nowrap" >
                          <g:if test="${d[1] != 0}"> <span class="label label-pill label-success">${d[1]}</span> &nbsp; </g:if>
                          <g:if test="${d[2] != 0}"> <span class="label label-pill label-danger">${d[2]}</span> &nbsp; </g:if>
                          <g:if test="${d[3] != 0}"> <span class="label label-pill label-warning">${d[3]}</span> </g:if>
                        </td>
                      </g:each>
                    </tr>
                  </g:each>
                </tbody>
              </table>
            </table>
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>
