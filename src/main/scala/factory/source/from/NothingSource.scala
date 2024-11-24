package factory.source.from

import factory.source.from.ISourceConnector

case class NothingSource(message: String) extends ISourceConnector {

  override def run(): Unit = {
    throw NotImplementedError()
  }

  override def stop(): Unit = {
    throw NotImplementedError()
  }
}
