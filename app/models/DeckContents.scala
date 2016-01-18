package models

import slick.driver.MySQLDriver.api._

case class DeckContent(deck: Int, card: String, version: String, quantity: Int)

class DeckContents(tag: Tag) extends Table[DeckContent](tag, "deck_contents_nonzero") {
	def deck = column[Int]("deck", O.PrimaryKey)
	def card = column[String]("card", O.PrimaryKey)
	def version = column[String]("version", O.PrimaryKey)
	def quantity = column[Int]("quantity")

	def * = (deck, card, version, quantity) <> (DeckContent.tupled, DeckContent.unapply)
}

object DeckContents extends TableQuery(new DeckContents(_))
