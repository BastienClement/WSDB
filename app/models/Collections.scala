package models

import slick.driver.MySQLDriver.api._

case class Collection(user: String, card: String, version: String, quantity: Int)

class Collections(tag: Tag) extends Table[Collection](tag, "collections") {
	def user = column[String]("user", O.PrimaryKey)
	def card = column[String]("card", O.PrimaryKey)
	def version = column[String]("version", O.PrimaryKey)
	def quantity = column[Int]("quantity")

	def * = (user, card, version, quantity) <> (Collection.tupled, Collection.unapply)
}

object Collections extends TableQuery(new Collections(_))
