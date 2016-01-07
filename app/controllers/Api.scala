package controllers

import com.google.inject.Inject
import controllers.mysql._
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results.Status
import scala.concurrent.Future

class Api extends Controller {
	def test = ApiAuthenticated.async {
		sql"SELECT name FROM cards WHERE version = 'a' LIMIT 50".as[String].map {
			cards => Ok(cards.zipWithIndex.map { case (n, i) => s"${i + 1}.\t$n" }.mkString("\n")).as("text/plain")
		}
	}
}
