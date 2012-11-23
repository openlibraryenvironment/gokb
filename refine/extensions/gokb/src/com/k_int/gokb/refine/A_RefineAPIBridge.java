package com.k_int.gokb.refine;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import com.google.refine.commands.Command;
import com.google.refine.util.ParsingUtilities;


public abstract class A_RefineAPIBridge extends Command {

    private static final String POST_LINE_END         = "\r\n";

    private static final String POST_HYPHENS          = "--";
    private static final String POST_BOUNDARY         = "*****";
    private static final String PROP_API_URL          = "http://localhost:8080/gokb/api/";
    private static final int    PROP_TIMEOUT          = 1800000;
    private static final int    POST_MAX_FILE_BUFFER  = 1*1024*1024;
    
    private static void postFilesAndParams(HttpURLConnection conn, Properties params, File[] files) throws IOException {

        DataOutputStream dos = new DataOutputStream( conn.getOutputStream() ); 
        try {

            // Add the params.
            if (params != null) {
                for (Object key : params.keySet()) {
                    dos.writeBytes(POST_HYPHENS + POST_BOUNDARY + POST_LINE_END); 
                    dos.writeBytes("Content-Disposition: form-data; name=\"" + key + "\"" + POST_LINE_END + POST_LINE_END);
                    dos.writeBytes(ParsingUtilities.encode(params.getProperty((String)key, "")) + POST_LINE_END);
                }
            }    

            // Add the files.
            if (files != null) {
                for (File file : files) {

                    FileInputStream fileInputStream = new FileInputStream (file);

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
    
    protected static String toParamString(Properties params) {
        String pString = "";
        if (params != null) {
            for (Object key : params.keySet()) {
                pString += 
                        (!"".equals(pString) ? "&" : "?") + key + "=" + ParsingUtilities.encode(params.getProperty((String)key, ""));
            }
        }
        return pString;
    }
    
    // TODO This Method only works with single parameter values. We should start
    // using the map internally instead of the Properties to allow for multiple values.
    protected static Properties parseParameters(HttpServletRequest request) {
        Properties props = new Properties ();
        
        @SuppressWarnings("unchecked")
        Map<String, String[]> params = request.getParameterMap();
        
        for (String key : params.keySet()) {
            props.put(key, params.get(key)[0]);
        }
        
        return props;
    }
    
    protected final void doAPIGet (String apiMethodCall, Properties params) throws Exception{
        doAPIGet (apiMethodCall, params, new RefineAPICallback());
    }
    
    protected final void doAPIGet (String apiMethodCall, Properties params, RefineAPICallback callback) throws Exception {

        // Return input stream.
        InputStream inputStream = null;

        // construct the url String
        String urlString = PROP_API_URL + apiMethodCall + toParamString (params);

        // Create a URL object.
        URL url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(PROP_TIMEOUT);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.connect();
        } catch (Exception e) {
            callback.onError (inputStream, new IOException("Cannot connect to " + urlString, e));
        }

        try {
            inputStream = connection.getInputStream();
            callback.onSuccess(inputStream);
        } catch (Exception e) {
            callback.onError(inputStream, new IOException("Cannot retrieve content from " + url, e));
        } finally {
            callback.complete(inputStream);
        }
    }

    protected final void doAPIPost (String apiMethodCall, Properties params, File[] files, RefineAPICallback callback) throws Exception {
        // Return input stream.
        InputStream inputStream = null;

        // construct the url String
        String urlString = PROP_API_URL + apiMethodCall + toParamString (params);

        // Create a URL object.
        URL url = new URL(urlString);
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);
            connection.setConnectTimeout(PROP_TIMEOUT);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Connection", "Keep-Alive"); 
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+POST_BOUNDARY);

            // Do the POST
            postFilesAndParams (connection, params, files);

            connection.connect();
        } catch (Exception e) {
            callback.onError (inputStream, new IOException("Cannot connect to " + urlString, e));
        }

        try {
            inputStream = connection.getInputStream();
            callback.onSuccess(inputStream);
        } catch (Exception e) {
            callback.onError(inputStream, new IOException("Cannot retrieve content from " + url, e));
        } finally {
            callback.complete(inputStream);
        }
    }
    
    protected final void doAPIPost (String apiMethodCall, Properties params, File[] files) throws Exception {
        doAPIPost (apiMethodCall, params, files, new RefineAPICallback());
    }
    
    protected final void doAPIPost (String apiMethodCall, Properties params) throws Exception {
        doAPIPost (apiMethodCall, params, (File[])null);
    }
    
    protected final void doAPIPost (String apiMethodCall, Properties params, RefineAPICallback callback) throws Exception {
        doAPIPost (apiMethodCall, params, null, callback);
    }
    
    protected final void doAPIPost (String apiMethodCall, File[] files) throws Exception {
        doAPIPost (apiMethodCall, null, files);
    }
    
    protected final void doAPIPost (String apiMethodCall, File[] files, RefineAPICallback callback) throws Exception {
        doAPIPost (apiMethodCall, null, files, callback);
    }
}