package controllers

import controllers.mysql._
import play.api.mvc._

case class Deck(id: Int, name: String, universes: Seq[(String, String)], ncx_count: Int, cx_count: Int)

object Collection {
	type UniverseItem = (String, String, String, Int, Int)
	type UniversesList = (Iterable[UniverseItem], Iterable[UniverseItem])
}

class Application extends Controller {
	def index = UserAction.async { implicit req =>
		sql"""
			SELECT a.id, u.id, u.name,
				(SELECT COUNT(*) FROM extensions AS e WHERE e.universe = u.id),
				(SELECT COUNT(*) FROM cards AS c JOIN extensions AS e ON e.id = c.extension WHERE e.universe = u.id)
			FROM universes AS u
				JOIN aspects AS a ON a.id = u.aspect
			ORDER BY a.id ASC, u.name ASC
	  """.as[(String, String, String, Int, Int)].run.map { universes =>
			Ok(views.html.index(universes.partition(u => u._1 == "W")))
		}
	}

	def decks = Authenticated.async { implicit req =>
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
				Deck(data.head._1, data.head._2, universes, data.map(_._5).sum, data.map(_._6).sum)
			}
		}.map {
			case decks => Ok(views.html.decks(decks))
		}
	}
}
