package com.k_int.gokb.module.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.json.JSONException;
import org.json.JSONObject;

import com.k_int.gokb.module.GOKbModuleImpl;

import com.google.refine.RefineServlet;
import com.google.refine.util.ParsingUtilities;

public class URLConenectionUtils {
  
  public static enum METHOD_TYPE {
    // TODO: Implement the other request methods in the framework. 
    POST, GET, PUT, HEAD, DELETE
  }

  private static final String POST_LINE_END         = "\r\n";
  
  private static final String POST_HYPHENS          = "--";

  private static final String POST_BOUNDARY         = "*****-REFINE_API_BRIDGE_BOUNDARY-*****";

  private static final int    POST_MAX_FILE_BUFFER  = 1*1024*1024;
  
  public static String getJSONFromStream(InputStream is) throws IOException, JSONException {
    return getJSONObjectFromStream(is).toString();
  }

  public static JSONObject getJSONObjectFromStream (InputStream is) throws JSONException, IOException {
    String json = ParsingUtilities.inputStreamToString(is);
    return ParsingUtilities.evaluateJsonStringToObject(json);
  }
  
  public static void writeFileData(DataOutputStream out, String fileName, String paramName, File file) throws IOException {
    writeFileData (out, fileName, paramName, new FileInputStream (file));
  }


  public static void writeFileData(DataOutputStream out, String fileName, String paramName, FileItem file) throws IOException {
    writeFileData (out, fileName, paramName, file.getInputStream());
  }

  public static void writeFileData(DataOutputStream out, String fileName, String paramName, InputStream in) throws IOException {

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

      // Send multipart form data necesssary after file data...
      out.writeBytes(POST_LINE_END);
    } finally {
      in.close();
    }
  }

  public static HttpURLConnection getAPIConnection(METHOD_TYPE type, URL url, String basicAuth) throws FileUploadException, IOException {

    // Connection.
    HttpURLConnection connection = null;

    // Configure the connection properties.
    connection = (HttpURLConnection) url.openConnection();
    connection.setDoOutput(true);
    connection.setUseCaches(false);
    connection.setConnectTimeout(GOKbModuleImpl.properties.getInt("timeout"));
    connection.setRequestProperty("Connection", "Keep-Alive");

    // Set the user-agent
    RefineServlet.setUserAgent(connection);

    // Set the custom refine extension property.
    connection.setRequestProperty("GOKb-version", GOKbModuleImpl.getVersion());

    // If we have user details set then we should use basic auth to add the details to the header.
    if (basicAuth != null) {
      connection.setRequestProperty  ("Authorization", "Basic " + basicAuth);
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
  
  public static HttpURLConnection getAPIConnection(METHOD_TYPE type, URL url) throws FileUploadException, IOException {
    return getAPIConnection(type, url, null);
  }
  
  public static void postFilesAndParams(HttpURLConnection conn, Map<String, String[]> params, Map<String, ?> files) throws IOException, FileUploadException {
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
  
  public static String paramString(Map<String, String[]> params) throws FileUploadException {

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
  
}
