package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.util.URLConenectionUtils;
import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;


public class GerericProxiedCommand extends A_RefineAPIBridge {

  final Logger logger;
  private final String remoteMethod;
  
  public GerericProxiedCommand(String remoteMethod) {
    this.remoteMethod = remoteMethod;
    this.logger = LoggerFactory.getLogger("GOKb-" + remoteMethod + "-proxied_command");
  }

  @Override
  public void doGet(HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    // Just proxy through to the remote app.
    try {
      getFromAPI(response, remoteMethod, params(request), new RefineAPICallback() {

        @Override
        protected void onSuccess(InputStream result, int responseCode)
            throws Exception {

          // Just send the api response through to the client.
          proxyReturn (response, result);
        }

      });
    } catch (Exception e) {

      // Respond with the error page.
      respondOnError(request, response, e);
    }
  }

  @Override
  public void doPost(HttpServletRequest request, final HttpServletResponse response)
      throws ServletException, IOException {
    // Just proxy through to the remote app.
    try {
      postToAPI(response, remoteMethod, params(request), files(request), new RefineAPICallback(){

        @Override
        protected void onSuccess(InputStream result, int responseCode)
            throws Exception {

          // Get the JSON back...
          String json = URLConenectionUtils.getJSONFromStream(result);

          // Send to the calling client.
          respond(response, json);
        }

      });
    } catch (Exception e) {

      // Respond with the error page.
      respondOnError(request, response, e);
    }
  }
}