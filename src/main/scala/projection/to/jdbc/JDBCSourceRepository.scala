package projection.to.jdbc

import grpc.projection.SourceProto.Statistics
import repository.scalike.ScalikeJdbcSession
import scalikejdbc.*


trait IJDBCSourceRepository {

  def insertSourceReading(source_id: String, aggregate_id: String, source_reading: String, session: ScalikeJdbcSession): Unit

  //def stats(source_id: String, key: String, session: ScalikeJdbcSession): Option[Statistics]
}

class JDBCSourceRepository extends IJDBCSourceRepository {

  override def insertSourceReading(source_id: String, aggregate_id: String, source_reading: String, session: ScalikeJdbcSession): Unit = {
    // TODO: Update source readings
    session.db.withinTx {
      implicit dbSession =>
        sql"""
              INSERT INTO sensor_readings (source_id, sensor_name, sensor_reading)
              VALUES ($source_id, $aggregate_id, $source_reading::jsonb);
             """.execute.apply()
    }
  }

  /*override def stats(source_id: String, key: String, session: ScalikeJdbcSession): Option[Statistics] = {
    // TODO: Take care of precision
    session.db.readOnly { implicit dbSession =>
      sql"""
      WITH readings AS (
        SELECT (sensor_reading ->>${key})::numeric AS reading
        FROM sensor_readings
        WHERE source_id =  ${source_id}
      )
      SELECT
        AVG(reading) AS mean,
        MAX(reading) AS highest_value,
        MIN(reading) AS lowest_value,
        VARIANCE(reading) AS var,
        COUNT(reading) AS total_events
      FROM readings""".map { result =>
        Statistics(source_id, result.float("mean"), result.float("highest_value"), result.float("lowest_value"), result.float("var"), result.int("total_events"))
      }.single.apply()
    }
  }*/
}
