package controllers

import play.api.mvc.{Action, Controller}
import play.api.routing.JavaScriptReverseRouter

/**
  * A simple error display controller.
  * It is automatically called if the user requests a page that does not exist.
  */
class Global extends Controller {
	/**
	  * Error 404
	  */
	def fourOhFour(path: String) = UserAction { implicit req =>
		NotFound(views.html.error("Page not found", "The page you requested does not exist.", s"${req.method} /$path"))
	}

	/**
	  * Routes file for javascript
	  */
	def jsRoutes = Action { implicit req =>
		Ok {
			JavaScriptReverseRouter("routes")(
				routes.javascript.Collection.update
			)
		}
	}
}
