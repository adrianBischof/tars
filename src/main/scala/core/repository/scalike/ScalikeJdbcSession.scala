package core.repository.scalike

import scalikejdbc.{ConnectionPool, DB}

import java.sql.Connection
import akka.japi.function.Function
import akka.projection.jdbc.JdbcSession


/**
 * Provide database connections within a transaction to Akka Projections.
 */
final class ScalikeJdbcSession extends JdbcSession {
  val db: DB = DB.connect(ConnectionPool.borrow())
  db.autoClose(false)

  override def withConnection[Result](func: Function[Connection, Result]): Result = {
    db.begin()
    db.withinTxWithConnection(func(_))
  }

  override def commit(): Unit = db.commit()

  override def rollback(): Unit = db.rollback()

  override def close(): Unit = db.close()
}

object ScalikeJdbcSession {
  def withSession[R](f: ScalikeJdbcSession => R): R = {
    val session = new ScalikeJdbcSession()
    try {
      f(session)
    } finally {
      session.close()
    }
  }
}
