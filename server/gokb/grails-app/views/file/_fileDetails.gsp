<g:if test="${filePath}">
    <div class="fileContents">
        <pre class="codeExample"><code>${fileContents}</code></pre>
    </div>

    <div class="downloadLink">
        <a href="${createLink(controller: 'file', action: 'downloadFile', params: [filePath: filePath])}">
            <g:message code="default.link.download.label" default="Download complete file"/>
        </a>
    </div>
</g:if>
<script type="text/javascript">
    function encodeAsCode(code) {
        code = code.replace(/&/mg, '&#38;');
        code = code.replace(/</mg, '&#60;');
        code = code.replace(/>/mg, '&#62;');
        code = code.replace(/\"/mg, '&#34;');
        code = code.replace(/\t/g, '  ');
        code = code.replace(/\r?\n/g, '<br>');
        code = code.replace(/<br><br>/g, '<br>');
        code = code.replace(/ /g, '&nbsp;');
        return code
    }
    $(document).ready(
            function() {
                var code = $('pre.codeExample>code').html();
                var formattedCode = encodeAsCode(code);
                $('pre.codeExample>code').html(formattedCode);
            }
    );
</script>