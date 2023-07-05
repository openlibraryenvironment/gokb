<g:if test="${filePath}">
    <div class="fileContents">
        <pre class="codeExample"><code class='codeExample' >${fileContents?.trim()?.encodeAsHTML()}</code></pre>
    </div>

    <div class="downloadLink">
        <a href="${createLink(controller: 'file', action: 'downloadFile', params: [filePath: filePath])}">
            <g:message code="default.link.download.label" default="Download complete file"/>
        </a>
    </div>
</g:if>
