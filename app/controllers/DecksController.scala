package controllers

import com.google.inject.Inject
import controllers.Decks.NewDeckData
import controllers.mysql._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import scala.concurrent.Future

object Decks {
	case class ListItem(id: Int, name: String, universes: Seq[(String, String)], ncx_count: Int, cx_count: Int)
	case class NewDeckData(name: String)
}

class DecksController @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
	val newDeckForm = Form(
		mapping(
			"name" -> nonEmptyText(1, 32)
		)(NewDeckData.apply)(NewDeckData.unapply)
	)

	/**
	  * Displays the list of decks created by the user.
	  */
	def list = Authenticated.async { implicit req =>
		val user = req.user.name
		sql"""
			SELECT DISTINCT d.id, d.name, dc.universe, dc.universe_name,
				(SELECT COALESCE(SUM(quantity), 0) FROM deck_cards
				 WHERE deck_id = d.id AND universe = dc.universe AND type != 'CX'),
				(SELECT COALESCE(SUM(quantity), 0) FROM deck_cards WHERE deck_id = d.id AND universe = dc.universe AND type = 'CX')
			FROM decks AS d
			LEFT JOIN deck_cards AS dc ON dc.deck_id = d.id
			WHERE d.user = $user
			ORDER BY d.name ASC, dc.universe_name ASC
		""".as[(Int, String, Option[String], Option[String], Int, Int)].run.map { case rows =>
			rows.pack(r => (r._1, r._2)).map { case data =>
				val universes = data.collect { case (_, _, uid, uname, _, _) if uid.isDefined => (uid.get, uname.get) }
				Decks.ListItem(data.head._1, data.head._2, universes, data.map(_._5).sum, data.map(_._6).sum)
			}
		}.map {
			case decks => Ok(views.html.decks(decks, newDeckForm))
		}
	}

	/**
	  * Create a new deck.
	  */
	def create = Authenticated.async { implicit req =>
		val form = newDeckForm.bindFromRequest
		form.fold(
			error => Redirect(routes.DecksController.list()).flashing("new_deck_err" -> "Invalid deck name"),
			_ => ???
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
