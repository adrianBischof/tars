package adapters

trait Connectable {

  def run(): Unit
  def stop(): Unit


}
