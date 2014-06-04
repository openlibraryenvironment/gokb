package com.k_int.gokb.module;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

public class Updator implements Runnable {

  private URL location;
  private File destination;

  public Updator (URL location, File destination) {
    this.location = location;
    this.destination = destination;
  }
  
  public void updateAndRestart () throws IOException {
    restart(this);
  }

  /** 
   * Sun property pointing the main class and its arguments. 
   * Might not be defined on none-Hotspot VM implementations.
   * TODO: Maybe need to look at more in depth method at
   * retrieving the command that works on more platforms.
   */
  public static final String SUN_JAVA_COMMAND = "sun.java.command";


  /**
   * Restart the current Java application
   * @throws IOException
   */
  public static void restart () throws IOException {
    restart (null);
  }

  /**
   * Restart the current Java application
   * @param runBeforeRestart some custom code to be run before restarting
   * @throws IOException
   */
  public static void restart (Runnable runBeforeRestart) throws IOException {
    try {

      // Java binary
      String java = System.getProperty("java.home") + "/bin/java";

      // VM arguments
      List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
      StringBuffer vmArgsOneLine = new StringBuffer();
      for (String arg : vmArguments) {

        // If it's the agent argument : we ignore it otherwise the
        // address of the old application and the new one will be in conflict
        if (!arg.contains("-agentlib")) {
          vmArgsOneLine.append(arg);
          vmArgsOneLine.append(" ");
        }
      }

      // Init the command to execute, add the vm args
      final StringBuffer cmd = new StringBuffer(java + " " + vmArgsOneLine);

      // Program main and program arguments
      String[] mainCommand = System.getProperty(SUN_JAVA_COMMAND).split(" ");

      // Program main is a jar
      if (mainCommand[0].endsWith(".jar")) {

        // If it's a jar, add -jar mainJar
        cmd.append("-jar " + new File(mainCommand[0]).getPath());
      } else {

        // Else it's a .class, add the classpath and mainClass
        cmd.append("-cp \"" + System.getProperty("java.class.path") + "\" " + mainCommand[0]);
      }

      // Finally add program arguments
      for (int i = 1; i < mainCommand.length; i++) {
        cmd.append(" ");
        cmd.append(mainCommand[i]);
      }

      // Execute the command in a shutdown hook, to be sure that all the
      // resources have been disposed of before restarting the application
      Runtime.getRuntime().addShutdownHook(new Thread() {
        @Override
        public void run() {
          try {
            Runtime.getRuntime().exec(cmd.toString());
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      });

      // Execute some custom code before restarting
      if (runBeforeRestart!= null) {
        runBeforeRestart.run();
      }

      // Exit
      System.exit(0);
    } catch (Exception e) {

      // Something went wrong
      throw new IOException("Error while trying to restart the application", e);
    }
  }

  /**
   * Download and extract the update for the module. We May need to restart the application too.
   * 
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run () {
    try {

      // Get the file extension.
      String ext = FilenameUtils.getExtension(location.getPath());

      // We only handle zips for now...
      if ("zip".equals(ext)) {

        // Now we have the file let's try and extract the contents.
        unzip(FileUtils.toFile(location), destination);
      }

    } catch (Exception e) {
      // TODO Auto-generated catch block
      e.printStackTrace();
    }
  }

  private void unzip (File from, File to_folder) throws IOException {
    
    // Create zip file entry.
    ZipFile zipFile = new ZipFile(from);
    
    // Each entry in the zip needs extracting.
    Enumeration<? extends ZipEntry> entries = zipFile.entries();
    while (entries.hasMoreElements()) {
      
      // Single entry.
      ZipEntry entry = entries.nextElement();
      
      // Extract to the destination.
      File entryDestination = new File(to_folder,  entry.getName());
      
      // We May need to create the directories.
      entryDestination.mkdirs();
      
      if (!entry.isDirectory()) {
        
        // This is a none directory type file. Let's save the data.
        InputStream in = zipFile.getInputStream(entry);
        OutputStream out = new FileOutputStream(entryDestination);
        IOUtils.copy(in, out);
        IOUtils.closeQuietly(in);
        IOUtils.closeQuietly(out);
      }
    }
  }
}