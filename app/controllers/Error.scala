package controllers

import play.api.mvc.Controller

/**
  * A simple error display controller.
  * It is automatically called if the user requests a page that does not exist.
  */
class Error extends Controller {
	/**
	  * Error 404
	  */
	def fourOhFour(path: String) = UserAction { implicit req =>
		NotFound(views.html.error("Error 404", "Page not found", s"${req.method} /$path"))
	}
}
