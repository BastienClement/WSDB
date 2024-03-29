package controllers

import com.google.inject.Inject
import models.{CompleteCards, Query}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.libs.json.Json
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
	  * Select a deck for editing.
	  */
	def select(id: Int) = Authenticated.async { implicit req =>
		for {
			owner <- Query.deckOwner(id)
			if owner == req.user.name
		} yield {
			Redirect(req.headers("referer")).addingToSession("deck" -> id.toString)
		}
	}

	/**
	  * Unselect the previously selected deck.
	  */
	def unselect = Authenticated { implicit req =>
		Redirect(req.headers("referer")).removingFromSession("deck")
	}

	/**
	  * Rename a deck.
	  */
	def rename = Authenticated.async { implicit req =>
		val form = Forms.NewDeck.bindFromRequest
		val redirect = Redirect(routes.Decks.list())
		form.fold(
			error => redirect.flashing("new_deck_err" -> "Invalid deck name"),
			{ case Forms.NewDeckData(name) =>
				Query.renameDeck(req.deck.get.id, name, req.user.name).map(_ => redirect)
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

	/**
	  * Display cards from a deck.
	  */
	def view(id: Int) = Authenticated.async { implicit req =>
		for {
			data <- Query.deckData(id, req.user.name)
			cards <- CompleteCards.filterDeck(id, req)
		} yield {
			val packed_cards = Query.packByCategory(cards) { case (cc, _, _) => cc }
			Ok(views.html.deck_content(data, packed_cards))
		}
	}

	/**
	  * Update card quantity in a deck
	  */
	def update = Authenticated.async(parse.urlFormEncoded) { implicit req =>
		val card = req.body("card").head
		val version = req.body("version").head
		val mod = req.body("mod").head.toInt
		Query.updateDeck(req.deck_id.get, card, version, mod).map { _ =>
			if (req.body.contains("deck")) Ok(Json.obj("success" -> views.html.components.deck().body))
			else Ok(Json.obj("success" -> true))
		}.recover {
			case e => Ok(Json.obj("error" -> e.getMessage))
		}
	}
}
