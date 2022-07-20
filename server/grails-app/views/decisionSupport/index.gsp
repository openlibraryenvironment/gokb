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
          <div class="col-md-6">
            <div class="panel-group">
              <div class="panel panel-default">
                <div class="panel-heading">
                  <div class="panel-heading" style="font-weight:bold;font-size:1.2em;">
                    <span>Options</span>
                    <span class="pull-right">
                      <a data-toggle="collapse" href="#dsfilters" style="font-weight:bold;"><i class="fas fa-angle-down"></i></a>
                    </span>
                  </div>
                </div>
                <div id="dsfilters" class="panel-collapse collapse in">
                  <div class="panel-body">
                    <g:form method="get" controller="decisionSupport" role="form">
                      <div class="form-horizontal">
                        <div class="form-group">
                          <label for="dimselect" class="col-sm-2 control-label">Component</label>
                          <div class="col-sm-8">
                            <select name="dimension" class="form-control">
                              <option value="Platform" ${params.dimension == 'Platform' ? 'selected' : ''}>Platform</option>
                              <option value="Package" ${params.dimension == 'Package' ? 'selected' : ''}>Package</option>
                            </select>
                          </div>
                        </div>
                        <div class="form-group">
                          <label for="qfilter" class="col-sm-2 control-label">Filter</label>
                          <div class="col-sm-8">
                            <input name="q" type="text" id="qfilter" class="form-control" value="${params.q}"/>
                          </div>
                        </div>
                        <div class="form-group">
                          <label for="maxres" class="col-sm-2 control-label">Results/page</label> 
                          <div class="col-sm-8">
                            <input type="number" id="maxres" class="form-control" style="width:80px;" name="max" min="1" max="1000" value="${max ?: 20}"/>
                          </div>
                        </div>
                      </div>
                      <div class="row" style="line-height:2em;">
                        <span class="col-sm-2">Categories</span>
                        <div class="col-sm-10">
                        
                          <g:each in="${cats}" var="c">
                            <g:if test="${ matrix.criterion_heads?.find { it.id as Long == c.id}}">
                              <span class="label label-pill label-success" title="${c.id}" style="font-size:1em">${c.description} <input type="checkbox" name="show_head" value="${c.id}" checked></span>
                            </g:if>
                            <g:else>
                              <span class="label label-pill label-default" title="${c.id}" style="font-size:1em">${c.description} <input type="checkbox" name="show_head" value="${c.id}"></span>
                            </g:else>
                          </g:each>
                        </div>
                        <br/>
                      </div>
                      <div style="clear:both;margin-top:10px">
                        <hr/>
                      </div>
                      <div class="btn-group pull-right" role="group" aria-label="Search Buttons">
                        <g:link controller="decisionSupport" action="index" params="${[dimension:params.dimension, searchAction:'search']}" class="btn btn-default">Clear Filters</g:link>
                        <button name="searchAction" type="submit" class="btn btn-default" value="search" style="margin-left:10px;">Apply</button>
                      </div>
                    </g:form>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="row">
          <div class="col-md-12">
            <div class="table-responsive">
              <table class="table table-striped table-bordered">
                <thead>
                  <tr>
                    <th rowspan="2" style="min-width:250px;">
                      <h2>${params.dimension} Name</h2>
                    </th>
                    <g:each in="${matrix.criterion_heads}" var="ch">
                      <th colspan="${ch?.count}" style="background-color:${ch.color?:'none'};"><g:link controller="resource" action="show" id="org.gokb.cred.DSCategory:${ch.id}">${ch?.name}</g:link></th>
                    </g:each>
                  </tr>
                  <tr>
                    <g:each in="${matrix.criterion}" var="c">
                      <th style="background-color:${c.color?:'none'};">
                        <g:link controller="resource" action="show" id="org.gokb.cred.DSCriterion:${c.id}" title="${c.explanation}">${c.description?:c.title}</g:link>
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
            <g:if test="${resultsTotal?:0 > 0 }" >
              <div class="pagination">
                <g:paginate controller="decisionSupport" action="index" params="${params}" next="&raquo;" prev="&laquo;" max="${max}" total="${resultsTotal}" />
              </div>
            </g:if>
          </div>
        </div>
      </g:form>
    </div>
  </body>
</html>
