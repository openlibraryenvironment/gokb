package com.k_int.gokb.refine.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.refine.A_RefineAPIBridge;
import com.k_int.gokb.refine.RefineAPICallback;


public class Lookup extends A_RefineAPIBridge {
  final static Logger logger = LoggerFactory.getLogger("GOKb-lookup_command");

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {
    handleRequest(request, response);
  }

  private void handleRequest (HttpServletRequest request, final HttpServletResponse response) {
    try {

      // Now we need to pass the data to the API.
      postToAPI(response, "lookup", params(request), files(request), new RefineAPICallback(){

        @Override
        protected void onSuccess(InputStream result, int responseCode)
            throws Exception {

          // Get the JSON from the client.
          BufferedReader rd = new BufferedReader(new InputStreamReader(result));
          StringBuilder sb = new StringBuilder();
          int cp;
          while ((cp = rd.read()) != -1) {
            sb.append((char) cp);
          }

          // The JSON parsed from GOKb.
          JSONObject theResponse = new JSONObject(sb.toString());

          // Send only the "result" object to the calling client.
          respond(response, theResponse.get("result").toString());
        }
      });

    } catch (Exception e) {

      // Respond with the error page.
      respondOnError(request, response, e);

    }
  }
}