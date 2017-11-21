package com.k_int.gokb.refine.commands;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONException;
import org.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.k_int.gokb.module.GOKbModuleImpl;

import com.google.refine.commands.Command;


public class GetCoreData extends Command {
  final static Logger logger = LoggerFactory.getLogger("GOKb-get-core-data_command");

  @Override
  public void doGet(HttpServletRequest request, HttpServletResponse response)
      throws ServletException, IOException {

    try {
      response.setCharacterEncoding("UTF-8");
      response.setHeader("Content-Type", "application/json");
      
      // The writer.
      JSONWriter writer = new JSONWriter(response.getWriter());
      GOKbModuleImpl.singleton.write(writer, null);
      
      // Do we need to close the app?
      if (GOKbModuleImpl.singleton.isUpdated()) {
        
        GOKbModuleImpl.singleton.scheduler.schedule(new Runnable(){

          @Override
          public void run () {
            // 5 second grace period.
            System.exit(0);
          }}, 5, TimeUnit.SECONDS);
      }
      
    } catch (JSONException e) {
      respondException(response, e);
    }
  }
}