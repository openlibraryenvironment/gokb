package com.k_int.gokb.module;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;

public class Updater {

  private URL location;
  private File destination;
  private File tempdir;

  public Updater (File tempdir, URL location, File destination) {
    this.tempdir = tempdir;
    this.location = location;
    this.destination = destination;
  }

  /**
   * Download and extract the update for the module. We May need to restart the application too.
   * @throws IOException 
   */
  public void update () throws IOException {

    // Get the file extension.
    String ext = FilenameUtils.getExtension(location.getPath());

    // We only handle zips for now...
    if ("zip".equals(ext)) {

      // Create a temporary file for the zip file.
      File dl = File.createTempFile("gokb_mod_update", ext);
      FileUtils.copyURLToFile(location, dl);

      // Now we have the file let's try and extract the contents.
      unzip(dl, destination);
    }
  }

  private void unzip (File from, File to_folder) throws IOException {

    // Simplify the path.
    to_folder = to_folder.getCanonicalFile();
    
    // Create a a temporary folder to unzip to.
    File temp_dir = File.createTempFile("unzip", File.separator, tempdir);
    
    // Need to delete here as the above method returns a file. We will
    // create it as a directory later on.
    temp_dir.delete();

    // Create zip file entry.
    ZipFile zipFile = null;
    
    try {
      zipFile = new ZipFile(from);

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