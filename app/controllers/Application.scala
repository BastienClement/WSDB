package controllers

import play.api.mvc._

class Application extends Controller {
	def index = UserAction { implicit req =>
		Ok(views.html.index())
	}
}
