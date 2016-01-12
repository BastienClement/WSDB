package controllers

import com.google.inject.Inject
import controllers.Decks.NewDeckData
import controllers.mysql._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import scala.concurrent.Future
import scala.util.Success
import slick.jdbc.GetResult

object Decks {
	case class ListRow(id: Int, name: String, universes: Seq[(String, String)], ncx_count: Int, cx_count: Int)

	case class NewDeckData(name: String)
	val NewDeckForm = Form(
		mapping(
			"name" -> nonEmptyText(1, 32)
		)(NewDeckData.apply)(NewDeckData.unapply)
	)
}

class DecksController @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
	import Decks._

	/**
	  * Displays the list of decks created by the user.
	  */
	def list = Authenticated.async { implicit req =>
		case class Deck(id: Int, name: String)
		case class Universe(id: Option[String], name: Option[String]) {
			require(id.isDefined == name.isDefined)
			def isDefined = id.isDefined
			def get = (id.get, name.get)
		}
		case class Count(ncx: Int, cx: Int)
		case class Row(deck: Deck, universe: Universe, count: Count)
		implicit val getRow = GetResult(r => Row(Deck(r.<<, r.<<), Universe(r.<<, r.<<), Count(r.<<, r.<<)))

		sql"""
			SELECT DISTINCT d.id, d.name, dc.universe, dc.universe_name,
				(SELECT IFNULL(SUM(quantity), 0) FROM deck_cards
				 WHERE deck_id = d.id AND universe = dc.universe AND type != 'CX'),
				(SELECT IFNULL(SUM(quantity), 0) FROM deck_cards
				 WHERE deck_id = d.id AND universe = dc.universe AND type = 'CX')
			FROM decks AS d
			LEFT JOIN deck_cards AS dc ON dc.deck_id = d.id
			WHERE d.user = ${req.user.name}
			ORDER BY d.name ASC, dc.universe_name ASC
		""".as[Row].run.map { case rows =>
			rows.pack(r => r.deck).map { data =>
				val deck = data.head.deck
				val universes = data.collect { case Row(_, universe, _) if universe.isDefined => universe.get }
				Decks.ListRow(deck.id, deck.name, universes, data.map(_.count.ncx).sum, data.map(_.count.cx).sum)
			}
		}.map { decks =>
			val form = req.flash.get("new_deck_err").map(err => NewDeckForm.withError("name", err)).getOrElse(NewDeckForm)
			Ok(views.html.decks(decks, form))
		}
	}

	/**
	  * Create a new deck.
	  */
	def create = Authenticated.async { implicit req =>
		val form = NewDeckForm.bindFromRequest
		val redirect = Redirect(routes.DecksController.list())
		form.fold(
			error => redirect.flashing("new_deck_err" -> "Invalid deck name"),
			data => {
				sqlu"""
					INSERT INTO decks
					SET name = ${data.name}, user = ${req.user.name}
				""".run.map(r => redirect).recover {
					case e => redirect.flashing("new_deck_err" -> "An error occured while creating the deck.")
				}
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
		} yield {
			val user = req.user.name
			sqlu"""
				DELETE FROM decks
				WHERE id = $id AND user = $user
				LIMIT 1
			""".run
		}).getOrElse(Future.successful(null)).recover { case _ => () }.map {
			_ => Redirect(routes.DecksController.list())
		}
	}
}
