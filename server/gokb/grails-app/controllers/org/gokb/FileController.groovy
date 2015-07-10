package org.gokb
class FileController {

    def fileLocations

    def index = {
        Map model = [locations: fileLocations.locations]
        if (params.filePath) {
            File file = new File(params.filePath)
            if (fileLocations.isValidPath(params.filePath) && file.exists()) {
                if (file.isFile()) {
                    List locations = getSubFiles(file.parentFile)
                    String fileContents = getFileContents(file)
                    model = [locations: locations, fileContents: fileContents, filePath: file.absolutePath]
                } else {
                    List locations = getSubFiles(file)
                    model = [locations: locations]
                }
                if (!fileLocations.locations.contains(file.absolutePath)) {
                    model['prevLocation'] = file.getParentFile()?.absolutePath
                }
                model['showBackLink'] = true
            } else {
                model.errorMessage = message(code: 'default.path.invalid.message')
            }
        }
        render(view: "/file/fileList", model: model, plugin: 'fileViewer')
    }

    def downloadFile = {
        File file = new File(params.filePath)
        byte[] assetContent = file.readBytes();
        response.setContentLength(assetContent.size())
        response.setHeader("Content-disposition", "attachment; filename=${file.name}")
        String contentType = FileViewerUtils.getMimeContentType(file.name.tokenize(".").last().toString())
        response.setContentType(contentType)
        OutputStream out = response.getOutputStream()
        out.write(assetContent)
        out.flush()
        out.close()
    }

    /**
     * getSubFiles  gets list of subfiles
     *
     * @param file
     *
     * @return List
     */
    private List getSubFiles(File file) {
        List<String> locations = []
        file.eachFile {File subFile ->
            locations << subFile.absolutePath
        }
        return locations
    }

    /**
     * getFileContents  reads file line by line
     *
     * @param file
     *
     * @return file contets formatted by <br/> html tag
     */
    private def getFileContents(File file) {
        String fileContents;
        List<String> contents = file.text.readLines()
        List<String> lines
        if (contents.size() > fileLocations.linesCount) {
            int startIndex = contents.size() - (fileLocations.linesCount + 1)
            int endIndex = contents.size()
            lines = contents.subList(startIndex, endIndex)
        } else {
            lines = contents
        }
        fileContents = lines*.encodeAsHTML().join("<br/>")
        return fileContents
    }
}
