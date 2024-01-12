package edu.ucr.cs.bdlab.davinci;

import edu.ucr.cs.bdlab.beast.common.BeastOptions;
import edu.ucr.cs.bdlab.beast.geolite.IFeature;
import edu.ucr.cs.bdlab.beast.io.SpatialReader;
import edu.ucr.cs.bdlab.beast.util.BeastServer;
import edu.ucr.cs.bdlab.test.JavaSparkTest;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.spark.api.java.JavaRDD;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class DaVinciServerTest extends JavaSparkTest {

  public void testSVGTile() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path inputData = new Path(scratchPath(), "input");
      copyResource("/test-mercator3.points", new File(inputData.toString()));
      // Plot the data
      Path plotData = new Path(scratchPath(), "plot");
      BeastOptions opts = new BeastOptions(false)
          .set("iformat", "point")
          .set("separator", ",")
          .set("mercator", true)
          .set("data-tiles", false)
          .set("plotter", "svgplot")
          .set("levels", 1);
      MultilevelPlot.run(opts, new String[] {inputData.toString()}, new String[] {plotData.toString()}, sparkContext());
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/visualize.cgi/%s/tile-0-0-0.svg", port, plotData));
      URLConnection urlConnection = url.openConnection();
      urlConnection.connect();
      if (urlConnection instanceof HttpURLConnection) {
        HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
        assertEquals(200, httpURLConnection.getResponseCode());
        String contentType = httpURLConnection.getHeaderField("Content-type");
        assertEquals("image/svg+xml", contentType);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testStaticSVGTile() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path inputData = new Path(makeResourceCopy("/test-mercator3.points").getPath());
      // Plot the data
      Path plotData = new Path(scratchPath(), "plot");
      BeastOptions opts = new BeastOptions(false)
          .set("iformat", "point")
          .set("separator", ",")
          .set("mercator", true)
          .set("threshold", 0)
          .set("data-tiles", false)
          .set("plotter", "svgplot")
          .set("levels", 1);
      MultilevelPlot.run(opts, new String[] {inputData.toString()}, new String[] {plotData.toString()}, sparkContext());
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false)
          .set("port", port)
          .set("datadir", scratchPath());
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/visualize.cgi/%s/tile-0-0-0.svg", port, plotData.getName()));
      URLConnection urlConnection = url.openConnection();
      urlConnection.connect();
      if (urlConnection instanceof HttpURLConnection) {
        HttpURLConnection httpURLConnection = (HttpURLConnection) urlConnection;
        assertEquals(200, httpURLConnection.getResponseCode());
        String contentType = httpURLConnection.getHeaderField("Content-type");
        assertEquals("image/svg+xml", contentType);
      }
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testHandleDownload() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path inputData = new Path(scratchPath(), "input");
      copyResource("/test-mercator3.points", new File(inputData.toString()));
      // Plot the data
      Path plotData = new Path(scratchPath(), "plot");
      BeastOptions opts = new BeastOptions(false)
          .set("iformat", "point")
          .set("separator", ",")
          .set("mercator", true)
          .set("data-tiles", false)
          .set("plotter", "gplot")
          .set("levels", 1);
      MultilevelPlot.run(opts, new String[] {inputData.toString()}, new String[] {plotData.toString()}, sparkContext());
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.csv.gz?point=true", port, plotData));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int compressedBufferSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, compressedBufferSize, buffer.length - compressedBufferSize)) > 0) {
        compressedBufferSize += readSize;
      }
      assert compressedBufferSize < buffer.length;
      inputStream.close();

      // Now, parse the result as text
      GZIPInputStream gzin = new GZIPInputStream(new ByteArrayInputStream(buffer, 0, compressedBufferSize));
      byte[] decompressedBuffer = new byte[4096];
      int decompressedBufferSize = 0;
      while ((readSize = gzin.read(decompressedBuffer, decompressedBufferSize, decompressedBuffer.length - decompressedBufferSize)) > 0) {
        decompressedBufferSize += readSize;
      }
      gzin.close();
      String str = new String(decompressedBuffer, 0, decompressedBufferSize);

      String[] lines = str.split("[\\r\\n]+");
      assertEquals("x\ty", lines[0]);
      assertEquals("-170.0\t90.0", lines[1]);
      assertEquals("170.0\t-90.0", lines[2]);
      assertEquals("-40.0\t0.0", lines[3]);

    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testDownloadFromIndexDirectly() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.csv.gz", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int compressedBufferSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, compressedBufferSize, buffer.length - compressedBufferSize)) > 0) {
        compressedBufferSize += readSize;
      }
      assert compressedBufferSize < buffer.length;
      inputStream.close();

      // Now, parse the result as text
      GZIPInputStream gzin = new GZIPInputStream(new ByteArrayInputStream(buffer, 0, compressedBufferSize));
      byte[] decompressedBuffer = new byte[4096];
      int decompressedBufferSize = 0;
      while ((readSize = gzin.read(decompressedBuffer, decompressedBufferSize, decompressedBuffer.length - decompressedBufferSize)) > 0) {
        decompressedBufferSize += readSize;
      }
      gzin.close();
      String str = new String(decompressedBuffer, 0, decompressedBufferSize);

      String[] lines = str.split("[\\r\\n]+");
      assertEquals(4, lines.length);
      assertEquals("LINESTRING (0 0, 1 1)\t1", lines[1]);
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testDownloadIndexAsGeoJSON() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/index.cgi/%s", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int bufferSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, bufferSize, buffer.length - bufferSize)) > 0) {
        bufferSize += readSize;
      }
      assert bufferSize < buffer.length;
      inputStream.close();

      // Simple check of the result
      String resultGeoJSON = new String(buffer, 0, bufferSize);
      assertTrue(String.format("Response '%s' did not contain Features", resultGeoJSON),
          resultGeoJSON.contains("FeatureCollection"));
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testDownloadWithDotInFileName() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index.test");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.csv.gz", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int compressedBufferSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, compressedBufferSize, buffer.length - compressedBufferSize)) > 0) {
        compressedBufferSize += readSize;
      }
      assert compressedBufferSize < buffer.length;
      inputStream.close();

      // Now, parse the result as text
      GZIPInputStream gzin = new GZIPInputStream(new ByteArrayInputStream(buffer, 0, compressedBufferSize));
      byte[] decompressedBuffer = new byte[4096];
      int decompressedBufferSize = 0;
      while ((readSize = gzin.read(decompressedBuffer, decompressedBufferSize, decompressedBuffer.length - decompressedBufferSize)) > 0) {
        decompressedBufferSize += readSize;
      }
      gzin.close();
      String str = new String(decompressedBuffer, 0, decompressedBufferSize);

      String[] lines = str.split("[\\r\\n]+");
      assertEquals(4, lines.length);
      assertEquals("LINESTRING (0 0, 1 1)\t1", lines[1]);
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testCompressedGeoJSON() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.geojson.gz", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] compressedData = new byte[4096];
      int dataSize = 0;
      int readSize;
      while ((readSize = inputStream.read(compressedData, dataSize, compressedData.length - dataSize)) > 0) {
        dataSize += readSize;
      }
      assert dataSize < compressedData.length;
      inputStream.close();

      // Now, parse the result as compressed file
      GZIPInputStream decompressedIn = new GZIPInputStream(new ByteArrayInputStream(compressedData, 0, dataSize));
      byte[] decompressedData = new byte[4096];
      dataSize = 0;
      while ((readSize = decompressedIn.read(decompressedData, dataSize, decompressedData.length - dataSize)) > 0) {
        dataSize += readSize;
      }
      String geojson = new String(decompressedData, 0, dataSize);
      assertTrue(geojson.contains("FeatureCollection"));
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testDownloadNonCompressedGeoJSON() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts2 = new BeastOptions(false).set("port", port);
      server.setup(opts2);
      Thread serverThread = new Thread(() -> server.run(opts2, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.geojson", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int dataSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, dataSize, buffer.length - dataSize)) > 0) {
        dataSize += readSize;
      }
      assert dataSize < buffer.length;
      inputStream.close();

      // Now, parse the result as text
      String geojson = new String(buffer, 0, dataSize);
      assertTrue(geojson.contains("FeatureCollection"));
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }


  public void testDownloadAsKMZFile() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts = new BeastOptions(false).set("port", port);
      server.setup(opts);
      Thread serverThread = new Thread(() -> server.run(opts, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.kmz", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int compressedBufferSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, compressedBufferSize, buffer.length - compressedBufferSize)) > 0) {
        compressedBufferSize += readSize;
      }
      assert compressedBufferSize < buffer.length;
      inputStream.close();

      // Now, parse the result back
      Path downloadedFile = new Path(scratchPath(), "downloadeFile.zip");
      FileSystem fileSystem = downloadedFile.getFileSystem(opts.loadIntoHadoopConf(new Configuration()));
      FSDataOutputStream out = fileSystem.create(downloadedFile);
      out.write(buffer, 0, compressedBufferSize);
      out.close();

      ZipFile zipFile = new ZipFile(downloadedFile.toString());
      ZipEntry kmlEntry = zipFile.getEntry("index.kml");
      String[] kmlFile = getLines(zipFile.getInputStream(kmlEntry), Integer.MAX_VALUE);
      int count = 0;
      for (String line : kmlFile) {
        while (line.contains("<Placemark")) {
          line = line.replaceFirst("<Placemark", "");
          count++;
        }
      }
      assertEquals(3, count);
    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }

  public void testDownloadAsShapefile() throws Exception {
    BeastServer server = new BeastServer();
    try {
      // Download should not convert data to Mercator
      Path indexPath = new Path(scratchPath(), "index");
      makeResourceCopy("/test_index", new File(indexPath.toString()));
      // Start the server
      int port = new Random().nextInt(10000) + 10000;
      BeastOptions opts = new BeastOptions(false).set("port", port);
      server.setup(opts);
      Thread serverThread = new Thread(() -> server.run(opts, null, null, javaSparkContext()));
      serverThread.start();
      // Wait until the server is started
      server.waitUntilStarted();

      // Send a request
      URL url = new URL(String.format("http://127.0.0.1:%d/dynamic/download.cgi/%s.zip", port, indexPath));
      InputStream inputStream = url.openStream();
      byte[] buffer = new byte[4096];
      int compressedBufferSize = 0;
      int readSize;
      while ((readSize = inputStream.read(buffer, compressedBufferSize, buffer.length - compressedBufferSize)) > 0) {
        compressedBufferSize += readSize;
      }
      assert compressedBufferSize < buffer.length;
      inputStream.close();

      // Now, parse the result back
      Path downloadedFile = new Path(scratchPath(), "downloadeFile.zip");
      FileSystem fileSystem = downloadedFile.getFileSystem(opts.loadIntoHadoopConf(new Configuration()));
      FSDataOutputStream out = fileSystem.create(downloadedFile);
      out.write(buffer, 0, compressedBufferSize);
      out.close();

      JavaRDD<IFeature> features = SpatialReader.readInput(javaSparkContext(), new BeastOptions(),
          downloadedFile.toString(), "shapefile");
      assertEquals(3, features.count());

    } finally {
      // Stop the server and wait until it is closed
      server.stop();
      server.waitUntilStopped();
    }
  }
}