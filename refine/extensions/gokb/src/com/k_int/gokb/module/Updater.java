package com.k_int.gokb.module;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.mit.simile.butterfly.ButterflyClassLoader;

public class Updater {
  
  final static Logger _logger = LoggerFactory.getLogger("GOKb-Updater");

  private ButterflyClassLoader cl;
  private File destination;
  private boolean downloaded = false;

  private boolean downloading = false;
  
  private GOKbService service;
  public Updater (GOKbService service, File destination, ButterflyClassLoader cl) {
    this.service = service;
    this.destination = destination;
    this.cl = cl;
  }
  
  private Path dl = null;
  
  public synchronized Path download() throws IOException, FileUploadException {
    downloading = true;
    downloaded = false;
    InputStream is = null;
    
    try {
    
      // Create a temporary file for the zip file.
      dl = Files.createTempFile("gokb_mod_update", "zip");
      
      // Get the update.
      _logger.info("Starting download of new GOKb module from {}", service.getURL() );
      is = service.getUpdatePackage().getInputStream();
   
      // Write to the temp file.
      Files.copy(is, dl, StandardCopyOption.REPLACE_EXISTING);
      _logger.info("Download of new module complete." );
      
      // Flag as not downloaded to ensure 
      downloaded = true;
      
    } finally {

      // Close the input stream.
      IOUtils.closeQuietly(is);
    }
    
    // Flag we are no longer downloading.
    downloading = false;
    return dl;
  }

  public GOKbService getService () {
    return service;
  }
  
  public synchronized boolean hasDownloaded() {
    return downloaded;
  }
  
  /**
   * Extract the update for the module. We May need to restart the application too.
   * @throws IOException 
   * @throws FileUploadException 
   */
  public synchronized void install () throws IOException {

    // Now we have the file let's try and extract the contents.
    _logger.info("Installing new GOKb module..." );
    unzip(dl, destination);
    
    dl = null;
  }

  public synchronized boolean isDownloading() {
    return downloading;
  }

  private synchronized void unzip (Path from, File to_folder) throws IOException {

    // Simplify the path.
    to_folder = to_folder.getCanonicalFile();
    
    // Create a a temporary folder to unzip to.
    File temp_dir = Files.createTempDirectory("unzip").toFile();

    // Create zip file entry.
    ZipFile zipFile = null;
    
    _logger.info("\tExtracting files from {} to {}", from.toString(), temp_dir.getAbsolutePath() );
    
    try {
      zipFile = new ZipFile(from.toFile());

      // Each entry in the zip needs extracting.
      Enumeration<? extends ZipEntry> entries = zipFile.entries();
      while (entries.hasMoreElements()) {

        // Single entry.
        ZipEntry entry = entries.nextElement();

        // Extract to the destination.
        File entryDestination = new File(temp_dir,  entry.getName());

        // We May need to create the directories.
        if (entry.isDirectory()) {
          entryDestination.mkdirs();

        } else {

          // Create every folder needed to create this file.
          entryDestination.getParentFile().mkdirs();

          // This is a none directory type file. Let's save the data.
          InputStream in = zipFile.getInputStream(entry);
          OutputStream out = new FileOutputStream(entryDestination);
          IOUtils.copy(in, out);
          IOUtils.closeQuietly(in);
          IOUtils.closeQuietly(out);
        }
      }
    } finally {
      
      // Ensure we close the Zip file.
      if (zipFile != null) zipFile.close();
    }
        
    _logger.info("\tMoving files from {} to {}", temp_dir.getAbsolutePath(), to_folder.getAbsolutePath() );
    
    // Close the class loader here...
    cl.close();
    for (File dir : temp_dir.listFiles((FileFilter)FileFilterUtils.directoryFileFilter())) {
      
      // First create the destination.
      File dest = new File (to_folder, dir.getName());
      
      // Remove the directory.
      FileUtils.deleteDirectory(dest);
      
      // Now move folder to the destination.
      FileUtils.moveDirectory(dir, dest);
    }
  }
}