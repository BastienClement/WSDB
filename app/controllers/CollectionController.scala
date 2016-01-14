package controllers

import java.io.ByteArrayInputStream

import controllers.Collection.UniverseRow
import controllers.mysql._
import play.api.mvc._
import slick.jdbc.GetResult

import scala.collection.mutable.ArrayBuffer

object Collection {
	case class UniverseRow(aspect: String, id: String, name: String, extensions: Int, cards: Int) {
		val weiss = aspect == "W"
		val schwarz = !weiss
	}

	case class UniverseExt(id: Int, name: String, abbreviation: String)
	implicit val GetUniverseExt = GetResult(r => UniverseExt(r.<<, r.<<, r.<<))

	type UniversesList = (Iterable[UniverseRow], Iterable[UniverseRow])
}

class CollectionController extends Controller {
	import Collection._

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

	def universe(id: String) = UserAction.async { implicit req =>
		val name = sql"SELECT name FROM universes WHERE id = $id".as[String].head.run
		val extensions = sql"""
			SELECT e.id, e.name, e.abbreviation
			FROM extensions AS e
			WHERE universe = $id
			ORDER BY e.abbreviation
		""".as[UniverseExt].run

		val result = for {
			n <- name
			exts <- extensions
		} yield {
			Ok(views.html.extension(n, exts))
		}

		result.recover {
			case _ => Redirect(routes.CollectionController.index());
		}
	}
}
