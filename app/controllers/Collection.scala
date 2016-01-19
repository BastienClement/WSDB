package controllers

import models.{CompleteCards, Query}
import play.api.mvc._

/**
  * Controller responsible for handling collection-related requests
  */
class Collection extends Controller {
	/**
	  * Index page
	  * Displays a list of every universes partitioned by aspect
	  */
	def index = UserAction.async { implicit req =>
		Query.universeList.map { rows =>
			// Partition the universe list into (Weiss, Schwarz) columns
			val columns = rows.partition(r => r.universe.aspect == "W")
			Ok(views.html.index(columns))
		}
	}

	/**
	  * Display every cards in a given universe
	  * The call to filterUniverse passes the request object from which
	  * user-provided filters can be extracted and used when querying the
	  * database.
	  */
	def universe(id: String) = UserAction.async { implicit req =>
		for {
			name <- Query.universeName(id)
			cards <- CompleteCards.filterUniverse(id, req)
		} yield {
			// Group cards by extensions
			val packed_cards = cards.pack { case (c, q) => c.extension_id }
			Ok(views.html.extension(name, packed_cards))
		}
	}

	/**
	  * Update the number of unit of a card in the collection
	  */
	def update = UserAction.async(parse.urlFormEncoded) { implicit req =>
		val card = req.body("card").head
		val version = req.body("version").head
		val mod = req.body("mod").head.toInt
		for (_ <- Query.updateCollection(req.user.name, card, version, mod)) yield {
			Ok("OK")
		}
	}

	/**
	  * Display card details.
	  */
	def card(id: String) = UserAction.async { implicit req =>
		val user = req.optUser.map(_.name).orNull
		(for {
			(card, quantity) <- Query.cardDetail(id, user)
			combos <- Query.cardCombos(card.id, user)
			decks <- Query.decksContaining(card.id, card.version, user)
		} yield {
			Ok(views.html.card(card, quantity, combos, decks))
		}).recover { case _ =>
			NotFound(views.html.error("Not found", "This cards does not seem to exist.", id))
		}
	}
}
