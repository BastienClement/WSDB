package models

import controllers._
import slick.driver.MySQLDriver.api._

case class CompleteCard(id: String, version: String, identifier: String, aspect: String, _u: (String, String),
                        _e: (Int, String), name: String, ability: String, flavor: String, rarity: String, tpe: String,
                        color: String, trigger: String, level: String, cost: String, power: String, soul: String,
                        _t1: (Int, String), _t2: (Int, String), limitation: Int) {
	val (universe_id, universe) = _u
	val (extension_id, extension) = _e
	val (trait1_id, trait1) = _t1
	val (trait2_id, trait2) = _t2
}

class CompleteCards(tag: Tag) extends Table[CompleteCard](tag, "complete_cards") {
	def id = column[String]("id", O.PrimaryKey)
	def version = column[String]("version", O.PrimaryKey)
	def identifier = column[String]("identifier")
	def aspect = column[String]("aspect")
	def universe_id = column[String]("universe_id")
	def universe = column[String]("universe")
	def extension_id = column[Int]("extension_id")
	def extension = column[String]("extension")
	def name = column[String]("name")
	def ability = column[String]("ability")
	def flavor = column[String]("flavor")
	def rarity = column[String]("rarity")
	def tpe = column[String]("type")
	def color = column[String]("color")
	def trigger = column[String]("trigger")
	def level = column[String]("level")
	def cost = column[String]("cost")
	def power = column[String]("power")
	def soul = column[String]("soul")
	def trait1_id = column[Int]("trait1_id")
	def trait1 = column[String]("trait1")
	def trait2_id = column[Int]("trait2_id")
	def trait2 = column[String]("trait2")
	def limitation = column[Int]("limitation")

	def * = (id, version, identifier, aspect, (universe_id, universe), (extension_id, extension), name, ability,
		flavor, rarity, tpe, color, trigger, level, cost, power, soul, (trait1_id, trait1), (trait2_id, trait2),
		limitation) <> (CompleteCard.tupled, CompleteCard.unapply)
}

object CompleteCards extends TableQuery(new CompleteCards(_)) {
	type CardQuery = Query[CompleteCards, CompleteCard, Seq]

	/**
	  * Filters cards matching given GET filters
	  */
	def cardsMatchingFilters(request: UserRequest[_]) = {
		val params = request.queryString.mapValues(v => v.head).toList

		def applyFilters(req: CardQuery, qs: List[(String, String)]): CardQuery = qs match {
			case (key, value) :: tail => applyFilters({
				lazy val int_val = value.toInt
				key match {
					case "rarity" => req.filter(_.rarity === value)
					case "level" => req.filter(_.level === value)
					case "type" => req.filter(_.tpe === value)
					case "trait" => req.filter(c => c.trait1_id === int_val || c.trait2_id === int_val)
					case "cost" => req.filter(_.cost === value)
					case _ => req
				}
			}, tail)
			case Nil => req
		}

		applyFilters(CompleteCards, params)
	}

	/**
	  * Add the quantity owned for each cards in the input query
	  */
	def joinCollectionQuantity(cards: CardQuery, request: UserRequest[_]) = {
		val user = request.optUser.map(_.name).getOrElse("")
		cards.joinLeft(Collections).on { case (card, col) =>
			col.card === card.id && col.version === card.version && col.user === user
		}.map { case (card, col) =>
			(card, col.map(_.quantity).ifNull(0))
		}
	}

	/**
	  * Get cards data for a given universe
	  */
	def filterUniverse(universe: String, request: UserRequest[_]) = {
		val cards =
			cardsMatchingFilters(request)
				.filter(_.universe_id === universe)
				.sortBy(_.identifier.asc)

		joinCollectionQuantity(cards, request).result.run
	}

	/**
	  * Get cards from a given deck
	  */
	def filterDeck(deck: Int, request: UserRequest[_]) = {
		val cards = cardsMatchingFilters(request)
		val cards_quantity = joinCollectionQuantity(cards, request)

		val full_query = cards_quantity.join(DeckContents).on { case ((card, quantity), cont) =>
			cont.card === card.id && cont.version === card.version && cont.deck === deck
		}.map { case ((card, quantity), cont) =>
			(card, quantity, cont.quantity)
		}.filter { case (card, col_quantity, deck_quantity) =>
			deck_quantity > 0
		}.sortBy { case (card, col_quantity, deck_quantity) =>
			(card.level.desc, card.tpe.asc)
		}

		full_query.result.run
	}
}
