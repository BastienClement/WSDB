package controllers

import play.api.mvc._

class Application extends Controller {
	def index = Action {
		Ok(views.html.index())
	}

	def login = Action {
		Ok(views.html.login())
	}

	def logon = Action {
		Ok(views.html.logon())
	}
}
