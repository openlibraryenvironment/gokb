<!DOCTYPE html>
<html>
<head>
<sec:ifLoggedIn>
<meta name='layout' content='sb-admin' />
</sec:ifLoggedIn>
<sec:ifNotLoggedIn>
<meta name='layout' content='public' />
</sec:ifNotLoggedIn>
<title>GOKb: Packages</title>
</head>

<body>

  <div class="container well">

    <g:if test="${message}">
      <div class="well">${message}</div>
    </g:if>

    <div class="row">

      <div class="col-lg-6">

      <h1>Manual Package Deposit</h1>
      <g:form controller="packages" action="deposit" enctype="multipart/form-data">
        <dt>Deposit file:</dt><dd> <input type="file" name="content"/></dd>
        <dt>Source:</dt><dd> <input type="text" name="source" value="${params.source?:'CUFTS'}"/></dd>
        <dt>Format:</dt>
        <dd> 
          <select name="fmt">
            <option value="kbart2">KBART</option>
            <g:each in="${grailsApplication.config.kbart2.mappings}" var="k,v">
              <option value="${k}">${k}</option>
            </g:each>
          </select>
        </dd>
        <dt>Package Name:</dt><dd> <input type="text" name="pkg" value="${params.pkg}"/></dd>
        <dt>Platform Url:</dt><dd> <input type="text" name="platformUrl" value="${params.platformUrl}"/></dd>
        <dt>Return Format:</dt><dd> <input type="text" name="format" value="${params.format?:'html'}"/></dd>
        <dt>Provider Name:</dt><dd> <input type="text" name="providerName" value="${params.providerName}"/></dd>
        <dt>Title Id Namespace:</dt><dd> <input type="text" name="providerIdentifierNamespace" value="${params.providerIdentifierNamespace}"/></dd>
        <dt>Reprocess:</dt><dd> <input type="text" name="reprocess" value="${params.reporocess?:'Y'}"/></dd>
        <dt>Incremental:</dt><dd> <input type="text" name="incremental" value="${params.incremental?:'N'}"/></dd>
        <dt>Synchronous:</dt><dd> <input type="text" name="synchronous" value="${params.synchronous?:'Y'}"/></dd>
        <dt>Flags:</dt><dd> <input type="text" name="flags" value="${params.flags}"/></dd>
        
        <button type="submit">Submit</button>
      </g:form>
      </div>
      <div class="col-lg-6">
        <h1>Scripted Package Deposit</h1>
        <pre>
 curl -v --user USERNAME:PASSWORD -X POST \
      --form content=@/path/to/filename.tsv \
      --form source="SOURCE_NAME" \
      --form fmt="format_id" \
      --form pkg="Package Name" \
      --form platformUrl="http://platform/url" \
      --form format="json" \
      --form providerName="Provider Name" \
      --form providerIdentifierNamespace="Title_id_Namespace" \
      --form reprocess="Y" \
      --form incremental="Y" \
      --form synchronous="Y" \
      --form flags="+ReviewNewTitles,+ReviewVariantTitles,+ReviewNewOrgs" \
      $GOKB_HOST/gokb/packages/deposit
        </pre>
      </div>
    </div>




  </div> <!-- /.container -->
</body>
</html>
