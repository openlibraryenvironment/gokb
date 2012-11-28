package com.k_int.gokb.refine;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
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

    private enum METHOD_TYPE {
        POST, GET
    }

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
    }
    private RequestObjects reqObj;

    private static final String POST_LINE_END         = "\r\n";

    private static final String POST_HYPHENS          = "--";

    private static final String POST_BOUNDARY         = "*****-REFINE_API_BRIDGE_BOUNDARY-*****";

    private static final String PROP_API_URL          = "http://localhost:8080/gokb/api/";
    private static final int    PROP_TIMEOUT          = 1800000;
    
    
    private static final int    POST_MAX_FILE_BUFFER  = 1*1024*1024;
    
    private static void postFilesAndParams(HttpURLConnection conn, Map<String, String[]> params, Map<String, ?> files) throws IOException, FileUploadException {
        DataOutputStream dos = new DataOutputStream( conn.getOutputStream() ); 
        try {

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

            // Add the file data based on the type.
            if (files != null) {
                for (String name : files.keySet()) {
                	Object fileData = files.get(name);
                	
                	if (fileData instanceof InputStream) {
                		
                		writeFileData (dos, name, (InputStream)fileData);
                		
                	} else if (fileData instanceof File) {
                		
                		writeFileData (dos, name, (File)fileData);
                		
                	} else if (fileData instanceof FileItem) {
                		
                		writeFileData (dos, name, (FileItem)fileData);
                	} else {
                		throw new InvalidClassException("Can't post file data for key " + name);
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
    
    private static void writeFileData(DataOutputStream out, String name, File file) throws IOException {
    	writeFileData (out, name, new FileInputStream (file));
    }
    
    private static void writeFileData(DataOutputStream out, String name, FileItem file) throws IOException {
    	writeFileData (out, name, file.getInputStream());
    }
    
    private static void writeFileData(DataOutputStream out, String name, InputStream in) throws IOException {
    	
    	try {
	        // Send a binary file
	        out.writeBytes(POST_HYPHENS + POST_BOUNDARY + POST_LINE_END); 
	        out.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + name +"\"" + POST_LINE_END); 
	        out.writeBytes(POST_LINE_END); 
	
	        // create a buffer of maximum size
	        int bytesAvailable = in.available(); 
	        int bufferSize = Math.min(bytesAvailable, POST_MAX_FILE_BUFFER); 
	        byte[] buffer = new byte[bufferSize]; 
	
	        // read file and write it into form... 
	
	        int bytesRead = in.read(buffer, 0, bufferSize); 
	
	        while (bytesRead > 0) 
	        { 
	            out.write(buffer, 0, bufferSize); 
	            bytesAvailable = in.available(); 
	            bufferSize = Math.min(bytesAvailable, POST_MAX_FILE_BUFFER); 
	            bytesRead = in.read(buffer, 0, bufferSize); 
	        } 
	
	        // send multipart form data necesssary after file data... 
	
	        out.writeBytes(POST_LINE_END);
    	} finally {
    		in.close();
    	}
    }
    
    protected Map<String, FileItem> files(HttpServletRequest request) throws FileUploadException {
        return req(request).files;
    }
    
    protected final void forwardToAPIGet (String apiMethod, HttpServletRequest request) throws Exception{
        forwardToAPIGet (apiMethod, request, new RefineAPICallback());
    }
    
    protected final void forwardToAPIGet (String apiMethod, HttpServletRequest request, RefineAPICallback callback) throws Exception {

        // Do the API get call.
        toAPI(METHOD_TYPE.GET, apiMethod, params(request), files(request), callback);
    }
    
    protected final void forwardToAPIPost (String apiMethod, HttpServletRequest request) throws Exception {
        forwardToAPIPost (apiMethod, request, new RefineAPICallback());
    }
    
    protected final void forwardToAPIPost (String apiMethod, HttpServletRequest request, RefineAPICallback callback) throws Exception {

    	// Do the API get call.
        toAPI(METHOD_TYPE.POST, apiMethod, params(request), files(request), callback);
    }
    
    protected HttpURLConnection getAPIConnection(METHOD_TYPE type, URL url) throws FileUploadException, IOException {
    	
        HttpURLConnection connection = null;
        
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
        
        return connection;
    }

    protected void getFromAPI (String apiMethod, Map<String, String[]> params) throws Exception {
    	// Get from API method.
    	getFromAPI(apiMethod, params, null);	
    }

    protected void getFromAPI (String apiMethod, Map<String, String[]> params, RefineAPICallback callback) throws Exception {
    	// Get from API method.
    	if (callback == null) callback = new RefineAPICallback();
    	toAPI(METHOD_TYPE.GET, apiMethod, params, null, callback);    	
    }

    private Map<String, String[]> params(HttpServletRequest request) throws FileUploadException {
        return req(request).params;
    }

    private String paramString(Map<String, String[]> params) throws FileUploadException {
    	
    	// Parameter string.
    	String pString = "";
    	if (params != null) {
    		for (String key : params.keySet()) {

    			// Get multiple values per parameter.
    			String[] vals = params.get(key);
    			for (String val : vals) {
    				
    				// Append each key, value pair to the string.
    				pString += 
    						(!"".equals(pString) ? "&" : "?") + key + "=" + ParsingUtilities.encode(val);
    			}
    		}
    	}
    	return pString;
    }

    protected void postToAPI (String apiMethod, Map<String, String[]> params) throws Exception {
    	// Post to API method.
    	postToAPI(apiMethod, params, null, null);
    }
    
    protected void postToAPI (String apiMethod, Map<String, String[]> params, Map<String, ?> fileData) throws Exception {
    	// Post to API method.
    	postToAPI(apiMethod, params, fileData, null);
    }
    
    protected void postToAPI (String apiMethod, Map<String, String[]> params, Map<String, ?> fileData, RefineAPICallback callback) throws Exception {
    	// Post to API method.
    	if (callback == null) callback = new RefineAPICallback();
    	toAPI(METHOD_TYPE.POST, apiMethod, params, fileData, callback);
    }
    
    private RequestObjects req(HttpServletRequest request) throws FileUploadException {
        if (reqObj == null) {
            reqObj = new RequestObjects (request);
        }
        return reqObj;
    }

    private final void toAPI (METHOD_TYPE type, String apiMethod, Map<String, String[]> params, Map<String, ?> fileData, RefineAPICallback callback) throws Exception {
        
        // Return input stream.
        InputStream inputStream = null;

        // construct the url String
        String urlString = PROP_API_URL + apiMethod;
        
        // If get then append the param string here.
        if (type == METHOD_TYPE.GET) {
            urlString += paramString(params);
        }

        // Create a URL object.
        URL url = new URL(urlString);

        try {
        	HttpURLConnection connection = getAPIConnection(type, url);
        	
            try {
                
                if (type == METHOD_TYPE.POST) {
                    // Do the POST.
                    postFilesAndParams (connection, params, fileData);
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
}