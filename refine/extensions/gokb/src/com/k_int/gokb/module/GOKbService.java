package com.k_int.gokb.module;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.util.ConditionalDownloader;
import com.k_int.gokb.module.util.URLConenectionUtils;

import com.google.refine.Jsonizable;

/**
 * Represents a remote GOKb web service.
 * 
 * @author Steve Osguthorpe <steve.osguthorpe@k-int.com>
 */
public class GOKbService extends A_ScheduledUpdates implements Jsonizable {
  public static final String SERVICE_DIR = "_gokb";
  
  final static Logger logger = LoggerFactory.getLogger("GOKb-Service");
  private boolean update = false;
  public boolean hasUpdate() {
    return update;
  }

  private class ServiceSettings {

    private Map<String,JSONObject> cache = new HashMap<String,JSONObject> ();

    private File directory;
    private ServiceSettings(File parentDir) {

      // Set the settings directory within the supplied directory.
      directory = new File(parentDir, SERVICE_DIR + File.separatorChar);
      logger.debug("Initialising settings handler using directory " + directory.getPath());
      directory.mkdir();
    }

    /**
     * Get the settings for the name supplied. The name will be used both as a key for the cache,
     * and also for the filename when saving to disk.
     * 
     * @param url String URL of settings.
     * @return
     * @throws IOException 
     * @throws JSONException 
     */
    private JSONObject get(String url) throws JSONException, IOException {

      // Hash the URL for cache key.
      String key = DigestUtils.md5Hex(url);

      // Read from the cache.
      JSONObject settings = cache.get(key);

      // We now need to load from the disk.
      File jsonFile = new File(directory, key + ".json");

      // Not present in the cache.
      if (settings == null) {

        if (jsonFile.exists()) {

          // Read the file.
          settings = new JSONObject(
              FileUtils.readFileToString(jsonFile)
              );
        } else {

          // We create a new JSONObject with null data and save.
          settings = new JSONObject("{\"etag\" : \"0\",\"data\" : {\"core\" : true}}");
          FileUtils.writeStringToFile(jsonFile, settings.toString());
        }

        // Store in the cache.
        cache.put(key, settings);
      }
      
      String etag = settings.has("etag") ? settings.getString("etag") : null;
      
      // Now we check if the service has an updated version.
      JSONObject update = getIfChanged (url, etag);
      if (update != null) {
        // Then the server has responded with new data. We should use that instead.
        settings = update;

        // We should write the contents to the disk too.
        FileUtils.writeStringToFile(jsonFile, settings.toString());

        // Store in the cache.
        cache.put(key, settings);
      }

      // Only return the data element of the object.
      return settings.getJSONObject("data");
    }

    /**
     * Get the data from the web service if it has changed or just retrieve from the local storage.
     * 
     * @param url
     * @param etag
     * @return
     * @throws IOException
     * @throws JSONException 
     */
    private JSONObject getIfChanged (String url, String etag) throws IOException, JSONException {

      URLConnection connection = ConditionalDownloader.getIfChanged(url, etag);
      if (connection != null) {
        // Try and read JSONObject form the server.
        BufferedReader in = null;
        
        try {
          in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

          // The string builder.
          StringBuilder str = new StringBuilder();
  
          // Read into a string builder.
          String inputStr;
          while ((inputStr = in.readLine()) != null) {
            str.append(inputStr);
          }
  
          String suppliedEtag = connection.getHeaderField("ETag");
          suppliedEtag = (suppliedEtag != null ? suppliedEtag : "0");
  
          // Now create the JSON from the text.
          return new JSONObject ("{\"etag\":\"" + suppliedEtag + "\",\"data\": " + str.toString() + "}");
        } finally {
          if (in != null) in.close();
        }
      }

      return null;
    }
  }
  
  private String availableModuleVersion = null;

  private String URL;
  
  private boolean alive = true;

  private ServiceSettings settings;

  private JSONObject capabilities = new JSONObject("{}");

  public JSONObject getCapabilities () {
    return capabilities;
  }

  private File directory;

  private boolean compatible = true;
  public File getDirectory () {
    return directory;
  }

  public GOKbService (String URL, File directory) throws IOException, JSONException, FileUploadException {
    this.URL = URL;
    this.directory = directory;
    this.settings = new ServiceSettings(directory);
    
    logger.debug("Initialising service at " + URL);

    initialise();
  }

  /**
   * Get the settings for a particular named value.
   * @param name
   * @return
   * @throws JSONException
   * @throws IOException
   */
  public JSONObject getSettings(String name) throws IOException, JSONException {
    logger.debug("Trying to get settings named '" + name + "'");
    return settings.get( getURL() + name );
  }

  /**
   * @return The URL of the service.
   */
  public String getURL () {
    return URL;
  }

  /**
   * Initialise the service object using data from the server.
   * @throws IOException 
   * @throws JSONException 
   * @throws FileUploadException 
   */
  private void initialise() throws JSONException, FileUploadException {
    try {

      capabilities = getSettings("capabilities");
      doScheduledUpdates ();
      
    } catch (IOException e) {
      // This exception will be thrown if the service was unavailable. We should still
      // allow the service to be initialized.
      logger.debug("Setting the capabilities resulted in an error. " + e.getMessage());
      logger.debug("Service is probably unavailable.");
      
      alive = false;
    }
  }

  /**
   * Check if the user we are connected to the service as is allowed to perform the operation supplied.
   * 
   * @param of The operation name.
   * @return true if allowed
   */
  public boolean isAllowed(String to) {
    return isCabable (to);
  }

  /**
   * Check if the service we are connected to is capable of the operation supplied.
   * 
   * @param of The operation name.
   * @return true if capable
   * @throws JSONException 
   */
  public boolean isCabable(String of) {
    try {
      return capabilities.has(of) && capabilities.getBoolean(of);
    } catch (JSONException e) {
      logger.error("Exception when testing capability '" + of + "'", e);
    }
    return false;
  }

  /**
   * Determines whether this service is responding to web requests.
   * 
   * @return true or false
   */
  public boolean isAlive() {
    return alive;
  }
  
  public boolean isCompatible() {
    return compatible ;
  }
  
  public String getAvailableModuleVersion() {
    return availableModuleVersion;
  }
  
  /**
   * Checks for an update. Returns true if one was found and sets available client version.
   * @return
   * @throws IOException
   * @throws JSONException 
   * @throws FileUploadException 
   */
  private boolean checkUpdate() throws IOException, JSONException, FileUploadException {
    
    JSONObject res = apiJSON(
      "checkUpdate",
      URLConenectionUtils.METHOD_TYPE.GET,
      URLConenectionUtils.paramStringMap(
        "tester=" + GOKbModuleImpl.properties.getBoolean("tester", false)
      )
    );
    
    // Get the current version we are using to send for comparison.
//    res = apiJSON("checkUpdate");
    if ("success".equalsIgnoreCase(res.getString("code"))) {
      
      res = res.getJSONObject("result");
      
      // Set the available version.
      availableModuleVersion = res.getString("latest-version");
      
      // SO: Temporary fix to stop old server versions forcing a downgrade of test versions of the module.
      String apiVersion = res.optString("api-version", null);
      if (apiVersion != null) {

        // Return whether there is an update.
        return res.getBoolean("update-available");
      }
    }
    return false;
  }
  
  public HttpURLConnection getUpdatePackage() throws FileUploadException, IOException {
    return callAPI(
      "downloadUpdate",
      URLConenectionUtils.METHOD_TYPE.POST,
      URLConenectionUtils.paramStringMap(
        "requested-version=" + availableModuleVersion
      )
    );
  }
  
  public JSONObject getCurrentUser() {
    JSONObject res;
    try {
      res = URLConenectionUtils.getJSONObjectFromStream(
        callSecureAPI(
          "userData",
          URLConenectionUtils.METHOD_TYPE.GET,
          null
        ).getInputStream()
      );
      if ("success".equalsIgnoreCase(res.getString("code"))) {
        
        res = res.getJSONObject("result");
      } else {
        res = null;
      }
    } catch (Exception e) { 
      res = null;
    }
    
    return res;
  }
  
  private JSONObject apiJSON (String apiMethod) throws JSONException, IOException, FileUploadException {
    return URLConenectionUtils.getJSONObjectFromStream( callAPI (apiMethod).getInputStream() );
  }
  
  private JSONObject apiJSON (String apiMethod, URLConenectionUtils.METHOD_TYPE methodType, Map<String, String[]> params) throws JSONException, IOException, FileUploadException {
    return URLConenectionUtils.getJSONObjectFromStream( callAPI (apiMethod, methodType, params).getInputStream() );
  }
  
  private HttpURLConnection callAPI (String apiMethod) throws IOException, FileUploadException {
    return callAPI (apiMethod, URLConenectionUtils.METHOD_TYPE.GET);
  }
  
  private HttpURLConnection callAPI (String apiMethod, URLConenectionUtils.METHOD_TYPE methodType) throws IOException, FileUploadException {
    return callAPI (apiMethod, methodType, null);
  }
  
  private HttpURLConnection callAPI (String apiMethod, URLConenectionUtils.METHOD_TYPE methodType, Map<String, String[]> params) throws FileUploadException, IOException {

    String urlString = URL + apiMethod;

    // If get then append the param string here.
    if (methodType == URLConenectionUtils.METHOD_TYPE.GET) {
      urlString += URLConenectionUtils.paramString(params);
    }

    // Create a URL object.
    URL url = new URL(urlString);
    
    // Open the connection.
    HttpURLConnection connection = URLConenectionUtils.getAPIConnection(methodType, url);
      
    // If we are posting then parameters should be written to the stream.
    if (methodType == URLConenectionUtils.METHOD_TYPE.POST) {
      URLConenectionUtils.postFilesAndParams(connection, params, null);
    }

    return connection;
  }
  
  private HttpURLConnection callSecureAPI (String apiMethod, URLConenectionUtils.METHOD_TYPE methodType, Map<String, String[]> params) throws FileUploadException, IOException {

    String urlString = URL + apiMethod;

    // If get then append the param string here.
    if (methodType == URLConenectionUtils.METHOD_TYPE.GET) {
      urlString += URLConenectionUtils.paramString(params);
    }

    // Create a URL object.
    URL url = new URL(urlString);
    
    // Open the connection with the .
    HttpURLConnection connection = URLConenectionUtils.getAPIConnection(methodType, url, GOKbModuleImpl.getCurrentUserDetails());
      
    // If we are posting then parameters should be written to the stream.
    if (methodType == URLConenectionUtils.METHOD_TYPE.POST) {
      URLConenectionUtils.postFilesAndParams(connection, params, null);
    }

    return connection;
  }

  @Override
  public void doScheduledUpdates () throws JSONException, IOException, FileUploadException {
    try {
      
      JSONObject res = apiJSON("isUp");
      if ("success".equalsIgnoreCase(res.getString("code"))) {
        alive = true;
        compatible = true;
        update = checkUpdate();
      } else {
        
        // We would only receive an error when running this command if we are running a version of the 
        // module which is too low for any server compatibility and so should flag as an alive but incompatible service.
        alive = true;
        compatible = false;
      }
    } catch (IOException e ) {
      // Service gone...
      logger.debug("Exception while doScheduledUpdates" + e.getMessage(),e);
      alive = false;
    }
  }

  @Override
  public void write (JSONWriter writer, Properties options)
      throws JSONException {
  
    writer.object()
      .key("capabilities").value(getCapabilities())
      .key("alive").value(isAlive())
      .key("compatible").value(isCompatible())
    .endObject();
    
  }
}