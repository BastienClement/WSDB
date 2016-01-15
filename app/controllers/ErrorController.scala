package controllers

import com.google.inject.Inject
import controllers.Decks.NewDeckData
import controllers.mysql._
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Controller
import scala.concurrent.Future
import scala.util.Success
import slick.jdbc.GetResult

class ErrorController extends Controller {
	def fourOhFour(path: String) = UserAction.async { implicit req =>
		NotFound(views.html.error("Error 404", "Page not found", s"${req.method} /$path"))
	}
}
