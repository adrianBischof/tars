package projection.to

import aggregate.SourceAggregate

trait IProjectionStore {
  
  def persist(event: SourceAggregate.Event): Unit

}
