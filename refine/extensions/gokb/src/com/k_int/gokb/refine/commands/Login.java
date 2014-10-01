package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.GOKbModuleImpl;
import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;


public class Login extends A_RefineAPIBridge {
  final static Logger logger = LoggerFactory.getLogger("GOKb-login_command");

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  private void handleRequest (final HttpServletRequest request, final HttpServletResponse response) {

    // Get the parameters submitted to this page.
    Map<String, String[]> params;
    try {
      params = params(request);

      // Get the parameters.
      String[] username = params.get("username");
      String[] password = params.get("password");

      // Ensure the details have been set.
      if (username != null && password != null && username.length == 1 && password.length == 1) {

        // Set the current user details.
        GOKbModuleImpl.setCurrentUserDetails(username[0], password[0]);
      }

      // Get the page.
      String referrer = request.getHeader("referer");
      final String page = referrer != null && !"".equals(referrer) ? referrer : "/";

      // Now we need to pass the data to the API.
      postToAPI(response, "checkLogin", params, null, new RefineAPICallback() {

        @Override
        protected void onSuccess(InputStream result, int responseCode) throws Exception {

          /* Do nothing */
        }

        @Override
        protected void onError(InputStream result, int respCode, Exception e) throws Exception {
          /* Do nothing */
        }

        @Override
        protected void complete(InputStream result) throws Exception {
          // Redirect
          response.sendRedirect(page);
        }
      });

    } catch (Exception e1) {

      // Error!
      respondOnError(request, response, e1);
    }
  }
}