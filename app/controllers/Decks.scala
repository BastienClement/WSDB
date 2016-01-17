package controllers

import com.google.inject.Inject
import models.{CompleteCards, Query}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller

class Decks @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
	/**
	  * Displays the list of decks created by the user.
	  */
	def list = Authenticated.async { implicit req =>
		Query.deckList(req.user.name).map { decks =>
			val form = req.flash.get("new_deck_err").map { err =>
				Forms.NewDeck.withError("name", err)
			}.getOrElse(Forms.NewDeck)
			Ok(views.html.decks(decks, form))
		}
	}

	/**
	  * Create a new deck.
	  */
	def create = Authenticated.async { implicit req =>
		val form = Forms.NewDeck.bindFromRequest
		val redirect = Redirect(routes.Decks.list())
		form.fold(
			error => redirect.flashing("new_deck_err" -> "Invalid deck name"),
			{ case Forms.NewDeckData(name) =>
				Query.createDeck(name, req.user.name).map(_ => redirect)
			}
		)
	}

	/**
	  * Invoked when deleting a deck.
	  */
	def delete = Authenticated.async(parse.urlFormEncoded) { implicit req =>
		(for {
			form_ids <- req.body.get("id")
			str_id <- form_ids.headOption
			id <- str_id.toOptInt
		} yield Query.deleteDeck(id, req.user.name)).get.map { _ =>
			Redirect(routes.Decks.list())
		}
	}

	def view(id: Int) = Authenticated.async { implicit req =>
		for {
			name <- Query.deckName(id)
			cards <- CompleteCards.filterDeck(id, req)
		} yield {
			val packed_cards = cards.packWithKey { case (card, _, _) => (card.tpe, card.level) }
			Ok(views.html.deck_content(name, packed_cards))
		}
	}
}
