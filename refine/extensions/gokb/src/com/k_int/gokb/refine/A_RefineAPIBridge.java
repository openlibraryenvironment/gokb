package com.k_int.gokb.refine;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.fileupload.FileUploadException;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.GOKbModuleImpl;
import com.k_int.gokb.module.RequestParser;
import com.k_int.gokb.module.util.URLConenectionUtils;
import com.k_int.gokb.refine.RefineAPICallback.GOKbAuthRequiredException;

import com.google.refine.commands.Command;


public abstract class A_RefineAPIBridge extends Command {

  private enum REQUEST_TYPE {
    NORMAL, AJAX
  }

  protected static void proxyReturn (HttpServletResponse clientResponse, InputStream apiResponse) throws IOException, JSONException, ServletException {
    // Get the JSON back...
    String json = URLConenectionUtils.getJSONFromStream(apiResponse);

    // Send to the calling client.  
    respond(clientResponse, json);
  }  

  final static Logger logger = LoggerFactory.getLogger("GOKb-Bridge-Command");
  
  public A_RefineAPIBridge () {
    super();
  }

  private REQUEST_TYPE determineRequestType (HttpServletRequest req) {

    if ("XMLHttpRequest".equalsIgnoreCase(req.getHeader("X-Requested-With"))) {
      return REQUEST_TYPE.AJAX;
    }

    // Default we should return normal HTML.
    return REQUEST_TYPE.NORMAL;
  }

  protected Map<String, Object> files(HttpServletRequest request) throws FileUploadException {
    return RequestParser.parse(request).getFiles();
  }
  
  protected final void forwardToAPIGet (HttpServletResponse clientResponse, String apiMethod, HttpServletRequest request) throws Exception{
    forwardToAPIGet (clientResponse, apiMethod, request, new RefineAPICallback());
  }

  protected final void forwardToAPIGet (HttpServletResponse clientResponse, String apiMethod, HttpServletRequest request, RefineAPICallback callback) throws Exception {

    // Do the API get call.
    toAPI(clientResponse, URLConenectionUtils.METHOD_TYPE.GET, apiMethod, params(request), files(request), callback);
  }

  protected final void forwardToAPIPost (HttpServletResponse clientResponse, String apiMethod, HttpServletRequest request) throws Exception {
    forwardToAPIPost (clientResponse, apiMethod, request, new RefineAPICallback());
  }

  protected final void forwardToAPIPost (HttpServletResponse clientResponse, String apiMethod, HttpServletRequest request, RefineAPICallback callback) throws Exception {

    // Do the API get call.
    toAPI(clientResponse, URLConenectionUtils.METHOD_TYPE.POST, apiMethod, params(request), files(request), callback);
  }

  protected void getFromAPI (HttpServletResponse clientResponse, String apiMethod, Map<String, String[]> params) throws Exception {
    // Get from API method.
    getFromAPI(clientResponse, apiMethod, params, null);	
  }

  protected void getFromAPI (HttpServletResponse clientResponse, String apiMethod, Map<String, String[]> params, RefineAPICallback callback) throws Exception {
    // Get from API method.
    if (callback == null) callback = new RefineAPICallback();
    toAPI(clientResponse, URLConenectionUtils.METHOD_TYPE.GET, apiMethod, params, null, callback);
  }

  protected Map<String, String[]> params(HttpServletRequest request) throws FileUploadException {
    return RequestParser.parse(request).getParams();
  }

  protected void postToAPI (HttpServletResponse clientResponse, String apiMethod, Map<String, String[]> params) throws Exception {
    // Post to API method.
    postToAPI(clientResponse, apiMethod, params, null, null);
  }

  protected void postToAPI (HttpServletResponse clientResponse, String apiMethod, Map<String, String[]> params, Map<String, Object> fileData) throws Exception {
    // Post to API method.
    postToAPI(clientResponse, apiMethod, params, fileData, null);
  }

  protected void postToAPI (HttpServletResponse clientResponse, String apiMethod, Map<String, String[]> params, Map<String, Object> fileData, RefineAPICallback callback) throws Exception {
    // Post to API method.
    if (callback == null) callback = new RefineAPICallback();
    toAPI(clientResponse, URLConenectionUtils.METHOD_TYPE.POST, apiMethod, params, fileData, callback);
  }

  /**
   * Decide how we should respond to the client browser depending on the type of error we have received.
   * 
   * @param clientRequest
   * @param clientResponse
   * @param e
   * @throws IOException
   * @throws ServletException
   * @throws JSONException
   */
  protected void respondOnError (HttpServletRequest clientRequest, HttpServletResponse clientResponse, Exception e) {

    try {
      switch (determineRequestType(clientRequest)) {
        case AJAX:
          // We need to feed back the data to refine in the format it is expecting from us.
          respondException(clientResponse, e);
          break;
        case NORMAL:
        default:
          respondWithErrorPage(clientRequest, clientResponse, e.getLocalizedMessage(), e);
          break;
      }
    } catch (Throwable t) {
      logger.error("Error when trying to send exception to client.", t);
    }
  }

  private final void toAPI (HttpServletResponse clientResponse, URLConenectionUtils.METHOD_TYPE methodType, String apiMethod, Map<String, String[]> params, Map<String, ?> fileData, RefineAPICallback callback) throws Exception {

    // Return input stream.
    InputStream inputStream = null;

    // Construct the URL String.
    // If the service url was not supplied when initiating then use the current workspace URL.
    String urlString = GOKbModuleImpl.singleton.getCurrentWorkspaceURL() + apiMethod;

    // If get then append the param string here.
    if (methodType == URLConenectionUtils.METHOD_TYPE.GET) {
      urlString += URLConenectionUtils.paramString(params);
    }

    // Create a URL object.
    URL url = new URL(urlString);

    try {
      // Open up a connection.
      HttpURLConnection connection = URLConenectionUtils.getAPIConnection(methodType, url, GOKbModuleImpl.getCurrentUserDetails());

      try {
        if (methodType == URLConenectionUtils.METHOD_TYPE.POST) {

          // Do the POST.
          URLConenectionUtils.postFilesAndParams (connection, params, fileData);
        }
      } catch (Exception e) {
        callback.onError (inputStream, connection.getResponseCode(), new IOException("Cannot connect to " + urlString, e));
      }
      try {
        try {

          // Input stream
          inputStream = connection.getInputStream();

        } catch (Exception e) {
          if (e instanceof FileNotFoundException) {
            // ignore
            inputStream = null;
          }
          else throw e;
        }

        // Run the success handler of the callback.
        callback.onSuccess(inputStream, connection.getResponseCode());
      } catch (Exception e) {

        // Run the error handler of the callback.
        callback.onError(inputStream, connection.getResponseCode(), new IOException("Cannot retrieve content from " + url, e));
      }
    } catch (GOKbAuthRequiredException e) {

      // Return the error to the client to display the login box.
      String message = GOKbModuleImpl.getCurrentUserDetails() != null ? "\"message\":\"The user details supplied are incorrect or you do not have permission to access the API.\", " : "";
      String content = "{"+ message + "\"result\":{\"errorType\":\"authError\"}, \"code\":\"error\"}";
      clientResponse.setCharacterEncoding("UTF-8");

      // Set the status to a 401 unauthorized.
      clientResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      Writer w = clientResponse.getWriter();
      if (w != null) {
        w.write(content);
        w.flush();
        w.close();
      } else {
        throw new ServletException("response returned a null writer");
      }

    } finally {

      // Run the complete handler of the callback.
      callback.complete(inputStream);
    }
  }
}
