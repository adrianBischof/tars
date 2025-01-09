package core.services.connectors

trait Connectable {
  
  def subscribe(): Unit

  def publish(command: String): Unit
  
  def terminate(): Unit
}
