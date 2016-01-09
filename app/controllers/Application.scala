package controllers

import controllers.mysql._
import play.api.mvc._

case class Deck(id: Int, name: String, universes: Seq[String], ncx_count: Int, cx_count: Int)

class Application extends Controller {
	def index = UserAction { implicit req =>
		Ok(views.html.index())
	}

	def decks = Authenticated.async { implicit req =>
		val user = req.user.name
		sql"""
			SELECT DISTINCT d.id, d.name, dc.universe_name,
				(SELECT COALESCE(SUM(quantity), 0) FROM deck_cards
				 WHERE deck_id = d.id AND universe = dc.universe AND type != 'CX') AS ncx,
				(SELECT COALESCE(SUM(quantity), 0) FROM deck_cards WHERE deck_id = d.id AND universe = dc.universe AND type = 'CX') AS cx
			FROM decks AS d
			LEFT JOIN deck_cards AS dc ON dc.deck_id = d.id
			WHERE d.user = $user
			ORDER BY d.name ASC, dc.universe_name ASC
		""".as[(Int, String, Option[String], Int, Int)].run map { case rows =>
			rows.pack(r => (r._1, r._2)).map { case data =>
				val universes = data.collect { case (_, _, u, _, _) if u.isDefined => u.get }
				Deck(data.head._1, data.head._2, universes, data.map(_._4).sum, data.map(_._5).sum)
			}
		} map {
			case decks => Ok(views.html.decks(decks))
		}
	}
}
