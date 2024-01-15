package ${groupId}

import edu.school.org.lab.rdpro.cg.SpatialJoinAlgorithms.{ESJDistributedAlgorithm, ESJPredicate}
import edu.school.org.lab.rdpro.geolite.{Feature, IFeature}
import org.apache.spark.SparkConf
import org.apache.spark.rdpro.{CRSServer, SparkSQLRegistration}
import org.apache.spark.rdd.RDD
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.types.{StringType, StructField, StructType}
import org.locationtech.jts.geom.{Envelope, GeometryFactory}

/**
 * Scala examples for rdpro
 */
object beastScala {
  def main(args: Array[String]): Unit = {
    // Initialize Spark context

    val conf = new SparkConf().setAppName("rdpro Example")
    // Set Spark master to local if not already set
    if (!conf.contains("spark.master"))
      conf.setMaster("local[*]")

    // Start the CRSServer and store the information in SparkConf
    val sparkSession: SparkSession = SparkSession.builder().config(conf).getOrCreate()
    val sparkContext = sparkSession.sparkContext
    CRSServer.startServer(sparkContext)
    SparkSQLRegistration.registerUDT
    SparkSQLRegistration.registerUDF(sparkSession)

    try {
      // Import rdpro features
      import edu.school.org.lab.rdpro._

      // Load spatial datasets
      // Load a shapefile.
      // Download from: ftp://ftp2.census.gov/geo/tiger/TIGER2018/STATE/
      val polygons = sparkContext.shapefile("tl_2018_us_state.zip")
      // Load points in GeoJSON format.
      val points = sparkContext.geojsonFile("Tweets_index.geojson")

      // Run a range query
      val geometryFactory: GeometryFactory = new GeometryFactory()
      val range = geometryFactory.toGeometry(new Envelope(-117.337182, -117.241395, 33.622048, 33.72865))
      val matchedPolygons: RDD[IFeature] = polygons.rangeQuery(range)
      val matchedPoints: RDD[IFeature] = points.rangeQuery(range)

      // Run a spatial join operation between points and polygons (point-in-polygon) query
      val sjResults: RDD[(IFeature, IFeature)] =
        matchedPolygons.spatialJoin(matchedPoints, ESJPredicate.Contains, ESJDistributedAlgorithm.PBSM)

      // Keep point coordinate, text, and state name
      val finalResults: RDD[IFeature] = sjResults.map(pip => {
        val polygon: IFeature = pip._1
        val point: IFeature = pip._2
        val values = point.toSeq :+ polygon.getAs[String]("NAME")
        val schema = StructType(point.schema :+ StructField("state", StringType))
        new Feature(values.toArray, schema)
      })

      // Write the output in CSV format
      finalResults.saveAsCSVPoints(filename = "output", xColumn = 0, yColumn = 1, delimiter = ';')
    } finally {
      sparkSession.stop()
    }
  }
}