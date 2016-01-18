package models

import slick.driver.MySQLDriver.api._

case class Deck(id: Int, user: String, name: String)

class Decks(tag: Tag) extends Table[Deck](tag, "decks") {
	def id = column[Int]("id", O.PrimaryKey)
	def user = column[String]("user")
	def name = column[String]("name")

	def * = (id, user, name) <> (Deck.tupled, Deck.unapply)
}

object Decks extends TableQuery(new Decks(_))
