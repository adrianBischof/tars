package factory.source.to.influx

import factory.source.to.ISinkConnector
import org.influxdb.InfluxDBFactory
import org.influxdb.dto.Point

class InfluxDB extends ISinkConnector {
  //TODO: load config from application.conf

  private val influxDB = InfluxDBFactory.connect("http://10.0.0.76:8086", "adrian", "NL3-USB3j1e2vL1vxMMHr-Z1k-IxYgPjS2CsRQCpAFvHozVjr4W4dE4cRcIVjy0QTBDqlB8hxUzr5w_cfPKdjQ==") //TODO: move connection string to application.conf
  influxDB.setDatabase("ActorBucket")

  override def persist(sourceId: String, unit: String, data: String): Unit = {
    //TODO: write data with point
    influxDB.write(s"weather,location=at,unit=$unit measurement=$data")
  }

}
