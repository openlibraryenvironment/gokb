package com.k_int.gokb.refine.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;


public class GerericProxiedCommand extends A_RefineAPIBridge {
    
    final Logger logger;
    private final String remoteMethod;
    public GerericProxiedCommand(String remoteMethod) {
        this.remoteMethod = remoteMethod;
        this.logger = LoggerFactory.getLogger("GOKb-" + remoteMethod + "-proxied_command");
    }
    
    private static String getJSONFromStream(InputStream is) throws IOException, JSONException {
      BufferedReader rd = new BufferedReader(new InputStreamReader(is));
      StringBuilder sb = new StringBuilder();
      int cp;
      while ((cp = rd.read()) != -1) {
        sb.append((char) cp);
      }
      return (new JSONObject(sb.toString())).toString();
    }
    
    
    @Override
    public void doGet(HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        // Just proxy through to the remote app.
        try {
            getFromAPI(remoteMethod, params(request), new RefineAPICallback(){

                @Override
                protected void onSuccess(InputStream result)
                        throws Exception {
                    
                    // Get the JSON back...
                    String json = getJSONFromStream(result);
                    
                    // Send to the calling client.
                    respond(response, json);
                }
                
            });
        } catch (Exception e) {
                
            // Respond with the error page.
            respondWithErrorPage(request, response, e.getLocalizedMessage(), e);
        }
    }
    
    @Override
    public void doPost(HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {
        // Just proxy through to the remote app.
        try {
            postToAPI(remoteMethod, params(request), files(request), new RefineAPICallback(){

                @Override
                protected void onSuccess(InputStream result)
                        throws Exception {
                    
                    // Get the JSON back...
                    String json = getJSONFromStream(result);
                    
                    // Send to the calling client.
                    respond(response, json);
                }
                
            });
        } catch (Exception e) {
                
            // Respond with the error page.
            respondWithErrorPage(request, response, e.getLocalizedMessage(), e);
        }
    }
}