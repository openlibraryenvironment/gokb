package com.k_int.gokb.refine;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FileCleaningTracker;

import com.google.refine.commands.Command;
import com.google.refine.util.ParsingUtilities;


public abstract class A_RefineAPIBridge extends Command {

    private class RequestObjects {
        private Map<String, FileItem> files = null;

        private Map<String, String[]> params = null;

        @SuppressWarnings("unchecked")
        private RequestObjects (HttpServletRequest request) throws FileUploadException {

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
                    files = new HashMap<String, FileItem> (fileItems.size());
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
            
            params = request.getParameterMap();
        }

        private String toParamString() {
            String pString = "";
            if (params != null) {
                for (Object key : params.keySet()) {

                    String[] vals = params.get(key);
                    for (String val : vals) {

                        pString += 
                                (!"".equals(pString) ? "&" : "?") + key + "=" + ParsingUtilities.encode(val);
                    }
                }
            }
            return pString;
        }
    }

    private RequestObjects reqObj;
    private static final String POST_LINE_END         = "\r\n";

    private static final String POST_HYPHENS          = "--";

    private static final String POST_BOUNDARY         = "*****-REFINE_API_BRIDGE_BOUNDARY-*****";

    private static final String PROP_API_URL          = "http://localhost:8080/gokb/api/";

    private static final int    PROP_TIMEOUT          = 1800000;
    private static final int    POST_MAX_FILE_BUFFER  = 1*1024*1024;
    protected final void doAPIGet (String apiMethodCall, HttpServletRequest request) throws Exception{
        doAPIGet (apiMethodCall, request, new RefineAPICallback());
    }
    protected final void doAPIGet (String apiMethodCall, HttpServletRequest request, RefineAPICallback callback) throws Exception {

        // Do the API get call.
        doAPIMethod(METHOD_TYPE.GET, apiMethodCall, request, callback);
    }
    protected final void doAPIPost (String apiMethodCall, HttpServletRequest request) throws Exception {
        doAPIPost (apiMethodCall, request, new RefineAPICallback());
    }
    
    private enum METHOD_TYPE {
        POST, GET
    }
    
    private final void doAPIMethod (METHOD_TYPE type, String apiMethodCall, HttpServletRequest request, RefineAPICallback callback) throws Exception {
        
        // Return input stream.
        InputStream inputStream = null;

        // construct the url String
        String urlString = PROP_API_URL + apiMethodCall;
        
        // If get then append the param string here.
        if (type == METHOD_TYPE.GET) {
            urlString += paramString(request);
        }

        // Create a URL object.
        URL url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            try {
                connection = (HttpURLConnection) url.openConnection();
                connection.setDoOutput(true);
                connection.setUseCaches(false);
                connection.setConnectTimeout(PROP_TIMEOUT);
                connection.setRequestProperty("Connection", "Keep-Alive");
                
                if (type == METHOD_TYPE.POST) {
                    connection.setDoInput(true);
                    connection.setRequestMethod("POST");
                    connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+POST_BOUNDARY);
                }
                
                // Connect to the service.
                connection.connect();
                
                if (type == METHOD_TYPE.POST) {
                    // Do the POST.
                    postFilesAndParams (connection, request);
                }
            } catch (Exception e) {
                callback.onError (inputStream, new IOException("Cannot connect to " + urlString, e));
            }
            try {

                // Get an input stream for the API response.
                inputStream = connection.getInputStream();

                // Run the success handler of the callback.
                callback.onSuccess(inputStream);
            } catch (Exception e) {

                // Run the error handler of the callback.
                callback.onError(inputStream, new IOException("Cannot retrieve content from " + url, e));
            }
        } finally {

            // Run the complete handler of the callback.
            callback.complete(inputStream);
        }
    }

    protected final void doAPIPost (String apiMethodCall, HttpServletRequest request, RefineAPICallback callback) throws Exception {

     // Do the API get call.
        doAPIMethod(METHOD_TYPE.POST, apiMethodCall, request, callback);
    }

    protected Map<String, FileItem> files(HttpServletRequest request) throws FileUploadException {
        return req(request).files;
    }

    private Map<String, String[]> params(HttpServletRequest request) throws FileUploadException {
        return req(request).params;
    }

    private String paramString(HttpServletRequest request) throws FileUploadException {
        return req(request).toParamString();
    }

    private void postFilesAndParams(HttpURLConnection conn, HttpServletRequest request ) throws IOException, FileUploadException {
        DataOutputStream dos = new DataOutputStream( conn.getOutputStream() ); 
        try {
            Map<String, String[]> params = params(request);

            // Add the params.
            if (params != null) {
                for (String key : params.keySet()) {
                    String[] vals = params.get(key);
                    for (String val : vals) {
                        dos.writeBytes(POST_HYPHENS + POST_BOUNDARY + POST_LINE_END); 
                        dos.writeBytes("Content-Disposition: form-data; name=\"" + key + "\"" + POST_LINE_END + POST_LINE_END);
                        dos.writeBytes(val + POST_LINE_END);
                    }
                }
            }    

            // Get the list of submitted files.
            Map<String, FileItem> files = files(request);

            // Add the files.
            if (files != null) {
                for (FileItem file : files.values()) {

                    InputStream fileInputStream = file.getInputStream();

                    try {
                        // Send a binary file
                        dos.writeBytes(POST_HYPHENS + POST_BOUNDARY + POST_LINE_END); 
                        dos.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + file.getName() +"\"" + POST_LINE_END); 
                        dos.writeBytes(POST_LINE_END); 

                        // create a buffer of maximum size
                        int bytesAvailable = fileInputStream.available(); 
                        int bufferSize = Math.min(bytesAvailable, POST_MAX_FILE_BUFFER); 
                        byte[] buffer = new byte[bufferSize]; 

                        // read file and write it into form... 

                        int bytesRead = fileInputStream.read(buffer, 0, bufferSize); 

                        while (bytesRead > 0) 
                        { 
                            dos.write(buffer, 0, bufferSize); 
                            bytesAvailable = fileInputStream.available(); 
                            bufferSize = Math.min(bytesAvailable, POST_MAX_FILE_BUFFER); 
                            bytesRead = fileInputStream.read(buffer, 0, bufferSize); 
                        } 

                        // send multipart form data necesssary after file data... 

                        dos.writeBytes(POST_LINE_END);
                    } finally {
                        // close streams 
                        fileInputStream.close();
                    }
                }
            }

            dos.writeBytes(POST_HYPHENS + POST_BOUNDARY + POST_HYPHENS + POST_LINE_END);
            dos.flush();
        } finally {
            // Close the data stream.
            dos.close();
        }
    }

    private RequestObjects req(HttpServletRequest request) throws FileUploadException {
        if (reqObj == null) {
            reqObj = new RequestObjects (request);
        }
        return reqObj;
    }
}