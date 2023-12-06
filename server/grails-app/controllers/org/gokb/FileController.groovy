package org.gokb

class FileController {

  def fileLocations

  def index() {
    
    Map model = [locations: fileLocations.locations]    
    def path = params.filePath;
    Long modified = null
    File f = null
    if (path && fileLocations.isValidPath(path)) {
      
      // Check the file exists!
      f = new File(path)
      if (f.exists() && f.isFile()) {
        modified = f.lastModified()
      }
    }
    
    // Curry vars as there are issues with accessing the variables from this scope.
    withCacheHeaders ({ File file, Long mod ->
      delegate.lastModified {
        mod
      }
      
      generate {
        if (file && file.exists()) {
          if (file.isFile()) {
            List locations = getSubFiles(file.parentFile)
            String fileContents = getFileContents(file)
            model = [locations: locations, fileContents: fileContents, filePath: file.absolutePath]

          } else {
            List locations = getSubFiles(file)
            model = [locations: locations]
          }
          if (!model.locations.contains(file.absolutePath)) {
            model['prevLocation'] = file.getParentFile()?.absolutePath
          }
          model['showBackLink'] = true
        }
        render(view: "/file/fileList", model: model, plugin: 'fileViewer')
      }
    }.curry(f, modified))
  }

  def downloadFile () {
    File file = new File(params.filePath)
    byte[] assetContent = file.readBytes();
    response.setContentLength(assetContent.size())
    response.setHeader("Content-disposition", "attachment; filename=${file.name}")
    // String contentType = FileViewerUtils.getMimeContentType(file.name.tokenize(".").last().toString())
    String contentType = "text"
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
