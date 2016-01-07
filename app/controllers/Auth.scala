package controllers

import com.google.inject.Inject
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._

case class LoginData(login: String, pass: String)
case class RegisterData(login: String, mail: String, pass: String)

class Auth @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
	val loginForm = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"pass" -> text
		)(LoginData.apply)(LoginData.unapply)
	)

	def login = UserAction { implicit req =>
		Ok(views.html.login())
	}

	val registerForm = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"mail" -> email,
			"pass" -> text
		)(RegisterData.apply)(RegisterData.unapply)
	)

	def register = UserAction { implicit req =>
		Ok(views.html.register(registerForm))
	}

	def registerPost = Action {
		Ok("OK !")
	}
}
