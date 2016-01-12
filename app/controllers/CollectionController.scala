package controllers

import controllers.Collection.UniverseRow
import controllers.mysql._
import play.api.mvc._
import slick.jdbc.GetResult

object Collection {
	case class UniverseRow(aspect: String, id: String, name: String, extensions: Int, cards: Int) {
		val weiss = aspect == "W"
		val schwarz = !weiss
	}

	type UniversesList = (Iterable[UniverseRow], Iterable[UniverseRow])
}

class CollectionController extends Controller {
	implicit val getUniverseRow = GetResult(r => UniverseRow(r.<<, r.<<, r.<<, r.<<, r.<<))
	def index = UserAction.async { implicit req =>
		sql"""
			SELECT a.id, u.id, u.name,
				(SELECT COUNT(*) FROM extensions AS e WHERE e.universe = u.id),
				(SELECT COUNT(*) FROM cards AS c JOIN extensions AS e ON e.id = c.extension WHERE e.universe = u.id)
			FROM universes AS u
				JOIN aspects AS a ON a.id = u.aspect
			ORDER BY a.id ASC, u.name ASC
		""".as[UniverseRow].run.map { universes =>
			Ok(views.html.index(universes.partition(u => u.weiss)))
		}
	}
}
