package com.k_int.gokb.refine;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.refine.RefineServlet;
import com.google.refine.commands.Command;
import com.google.refine.util.ParsingUtilities;


public abstract class A_RefineAPIBridge extends Command {

    private enum METHOD_TYPE {
        // TODO: Implement the other request methods in the framework. 
        POST, GET, PUT, HEAD, DELETE
    }

    private static final String POST_LINE_END         = "\r\n";

    private static final String POST_HYPHENS          = "--";

    private static final String POST_BOUNDARY         = "*****-REFINE_API_BRIDGE_BOUNDARY-*****";
    
    private static final int    POST_MAX_FILE_BUFFER  = 1*1024*1024;
    
    protected static String getJSONFromStream(InputStream is) throws IOException, JSONException {
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();
      int cp;
      while ((cp = rd.read()) != -1) {
        sb.append((char) cp);
      }
      return (new JSONObject(sb.toString())).toString();
    }
    
    protected static void proxyReturn (HttpServletResponse clientResponse, InputStream apiResponse) throws IOException, JSONException, ServletException {
        // Get the JSON back...
        String json = getJSONFromStream(apiResponse);
        
        // Send to the calling client.
        respond(clientResponse, json);
    }
    
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
                		
                		writeFileData (dos, name, name, (InputStream)fileData);
                		
                	} else if (fileData instanceof ByteArrayOutputStream) {    
                	    // Copy data to a byteArrayInputStream...
                	    ByteArrayOutputStream out = (ByteArrayOutputStream)fileData;
                	    
                	    try {
                                writeFileData (
                                   dos,
                                   name,
                                   name,
                                   new ByteArrayInputStream(
                                      ((ByteArrayOutputStream)fileData).toByteArray()
                                   )
                                );
                	    } finally {
                            
                                // Close the output stream.
                                out.close();
                            }
                        } else if (fileData instanceof File) {
                            File f = (File)fileData;
                            writeFileData (dos, f.getName() ,name, f);
                		
                	} else if (fileData instanceof FileItem) {
                	    FileItem f = (FileItem)fileData;
                            writeFileData (dos, f.getName(), name, f);
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
    
    private static void writeFileData(DataOutputStream out, String fileName, String paramName, File file) throws IOException {
    	writeFileData (out, fileName, paramName, new FileInputStream (file));
    }
    
    private static void writeFileData(DataOutputStream out, String fileName, String paramName, FileItem file) throws IOException {
    	writeFileData (out, fileName, paramName, file.getInputStream());
    }
    
    private static void writeFileData(DataOutputStream out, String fileName, String paramName, InputStream in) throws IOException {
    	
    	try {
	        // Send a binary file
	        out.writeBytes(POST_HYPHENS + POST_BOUNDARY + POST_LINE_END); 
	        out.writeBytes("Content-Disposition: form-data; name=\"" + paramName + "\";filename=\"" + fileName +"\"" + POST_LINE_END); 
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
    
    protected Map<String, Object> files(HttpServletRequest request) throws FileUploadException {
        return RequestParser.parse(request).getFiles();
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
        connection.setConnectTimeout(GOKbModuleImpl.properties.getInt("timeout"));
        connection.setRequestProperty("Connection", "Keep-Alive");
        
        // Set the user-agent
        RefineServlet.setUserAgent(connection);
        
        // Set the custom refine extension property.
        connection.setRequestProperty("GOKb-version", GOKbModuleImpl.VERSION);
        
        // If we have user details set then we should use basic auth to add the details to the header.
        String details = GOKbModuleImpl.getCurrentUserDetails();
        if (details != null) {
            connection.setRequestProperty  ("Authorization", "Basic " + details);
        }
        
        // Now do the specifics.
        if (type == METHOD_TYPE.POST) {
            connection.setDoInput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; charset=UTF-8; boundary="+POST_BOUNDARY);
        } else if (type == METHOD_TYPE.GET) {
        	connection.setRequestMethod("GET");
            connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
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

    protected Map<String, String[]> params(HttpServletRequest request) throws FileUploadException {
        return RequestParser.parse(request).getParams();
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
    
    protected void postToAPI (String apiMethod, Map<String, String[]> params, Map<String, Object> fileData) throws Exception {
    	// Post to API method.
    	postToAPI(apiMethod, params, fileData, null);
    }
    
    protected void postToAPI (String apiMethod, Map<String, String[]> params, Map<String, Object> fileData, RefineAPICallback callback) throws Exception {
    	// Post to API method.
    	if (callback == null) callback = new RefineAPICallback();
    	toAPI(METHOD_TYPE.POST, apiMethod, params, fileData, callback);
    }

    private final void toAPI (METHOD_TYPE type, String apiMethod, Map<String, String[]> params, Map<String, ?> fileData, RefineAPICallback callback) throws Exception {
        
        // Return input stream.
        InputStream inputStream = null;

        // construct the url String
        String urlString = GOKbModuleImpl.properties.getString("api.url") + apiMethod;
        
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
                try {
                    inputStream = connection.getInputStream();
                } catch (Exception e) {
                    if (e instanceof FileNotFoundException) {
                        // ignore
                        inputStream = null;
                    }
                    else throw e;
                }

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
