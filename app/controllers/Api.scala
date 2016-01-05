package controllers

import controllers.mysql._
import play.api.mvc._

class Api extends Controller {
	def test = Action.async {
		sql"SELECT name FROM cards WHERE version = 'a' LIMIT 50".as[String].map {
			cards => Ok(cards.zipWithIndex.map { case (n, i) => s"${i + 1}.\t$n" }.mkString("\n")).as("text/plain")
		}
	}
}
