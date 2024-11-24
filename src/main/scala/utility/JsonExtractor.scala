package utility

import io.circe.*
import io.circe.parser.*


object JsonExtractor {


  def extract(jsonString: String, key: String): Option[Json] = {
    def isOfBasicDataType(json: Json): Boolean = json.isBoolean || json.isNumber || json.isString || json.isNull


    parse(jsonString) match {
      case Left(failure) =>
        println(s"Invalid JSON: $failure")
        None
      case Right(json) =>
        // Split the keyPath into parts
        val keys = key.split("\\.")

        // Navigate through the JSON structure using hcursor
        keys.foldLeft(Option(json.hcursor)) { (accCursorOpt, key) =>
          accCursorOpt.flatMap(_.downField(key).focus.map(_.hcursor))
        }.flatMap(_.focus match {
          case Some(value) if isOfBasicDataType(value) => Some(value)
          case _ => None
        })
    }
  }
}