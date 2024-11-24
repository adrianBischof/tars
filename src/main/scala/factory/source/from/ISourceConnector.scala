package factory.source.from

trait ISourceConnector {
  
  def run(): Unit
  def stop(): Unit

}
