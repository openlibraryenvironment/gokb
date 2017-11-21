package com.k_int.gokb.module;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileCleaningTracker;

import com.google.refine.util.ParsingUtilities;


public class RequestParser {
    public static RequestParser parse (HttpServletRequest request) throws FileUploadException {
        return new RequestParser (request);
    }
    
    // The files uploaded.
    private Map<String, Object> files = new HashMap<String, Object> ();

    // The text parameters
    private Map<String, String[]> params = new HashMap<String, String[]> ();

    // The last request to ask for the parameters.
    // Used to keep the parameters for access throughout the request,
    // and not re-parse.
    private HttpServletRequest request = null;
    
    @SuppressWarnings("unchecked")
    private RequestParser (HttpServletRequest request) throws FileUploadException {

        // Only initialise if necessary.
        if (request == null || !request.equals(this.request)) {
        
            // Check that it is multipart
            if (ServletFileUpload.isMultipartContent(request)) {
    
                // Create a factory for disk-based file items.
                DiskFileItemFactory fileItemFactory = new DiskFileItemFactory();
                fileItemFactory.setFileCleaningTracker(new FileCleaningTracker());
                ServletFileUpload upload = new ServletFileUpload(fileItemFactory);
    
                // Parse the files from the request
                List<FileItem> fileItems = upload.parseRequest(request);
    
                // Create a map of name -> FileItems 
                if (fileItems.size() > 0) {
                    files = new HashMap<String, Object> (fileItems.size());
                    for (FileItem file : fileItems) {
                        files.put(file.getFieldName(), file);
                    }
                }
            }
    
            // Create params and ensure they are decoded.
            params = new HashMap<String, String[]>() {
                private static final long serialVersionUID = 6346625315535415606L;
    
                @Override
                public String[] get(Object key) {
                    String[] value = super.get(key);
                    if (value != null) {
                        for (int i=0; i< value.length; i++) {
                            value[i] = ParsingUtilities.decode(value[i]);
                        }
                    }
                    return value;
                }
            };
            
            params.putAll(request.getParameterMap());
            
            // Set the request that these parameters came from for reuse,
            // within the same request.
            this.request = request;
        }
    }
    
    // Method to get the files either by parsing the request or by
    // supplying the ones parsed from this request earlier.
    public Map<String, Object> getFiles() {
        return files;
    }
    
    // Method to get the parameters either by parsing the request or by
    // supplying the ones parsed from this request earlier. 
    public Map<String, String[]> getParams() {
        return params;
    }
}