package factory.source.to

trait ISinkConnector {
  
  def persist(sourceId: String, unit: String, data: String): Unit

}
