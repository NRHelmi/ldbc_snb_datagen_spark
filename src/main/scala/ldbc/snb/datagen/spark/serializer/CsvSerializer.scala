package ldbc.snb.datagen.spark.serializer

import ldbc.snb.datagen.spark.model.DataFrameGraph

import better.files._
import ldbc.snb.datagen.model.EntityType
import ldbc.snb.datagen.spark.util.Utils.snake

case class CsvSerializer(root: String, header: Boolean = false, separator: Char = '|') {

  import EntityPath._

  def serialize(graph: DataFrameGraph) = {
    graph.entities.foreach {
      case (tpe, dataset) => dataset.write
        .format("csv")
        .options(Map(
          "header" -> header.toString,
          "sep" -> separator.toString
        ))
        .save((root / "csv" / snake(graph.layout) / EntityPath[EntityType].entityPath(tpe)).toString)
    }
  }
}
