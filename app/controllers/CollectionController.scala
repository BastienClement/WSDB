package controllers

import controllers.Collection.UniverseInfo
import controllers.mysql._
import play.api.mvc._

object Collection {
	case class UniverseInfo(aspect: String, id: String, name: String, extensions: Int, cards: Int) {
		val weiss = aspect == "W"
		val schwarz = !weiss
	}

	type UniversesList = (Iterable[UniverseInfo], Iterable[UniverseInfo])
}

class CollectionController extends Controller {
	def index = UserAction.async { implicit req =>
		sql"""
			SELECT a.id, u.id, u.name,
				(SELECT COUNT(*) FROM extensions AS e WHERE e.universe = u.id),
				(SELECT COUNT(*) FROM cards AS c JOIN extensions AS e ON e.id = c.extension WHERE e.universe = u.id)
			FROM universes AS u
				JOIN aspects AS a ON a.id = u.aspect
			ORDER BY a.id ASC, u.name ASC
		""".as[(String, String, String, Int, Int)].run.map { universes =>
			Ok(views.html.index(universes.map(UniverseInfo.tupled).partition(u => u.weiss)))
		}
	}
}
