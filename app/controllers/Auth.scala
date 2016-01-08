package controllers

import com.google.inject.Inject
import java.sql.SQLIntegrityConstraintViolationException
import play.api
import play.api.data
import play.api.data._
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import controllers.mysql._

case class LoginData(login: String, pass: String)
case class RegisterData(login: String, mail: String, pass: String)

class Auth @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
	val loginForm = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"pass" -> nonEmptyText(6)
		)(LoginData.apply)(LoginData.unapply)
	)

	def login = UserAction { implicit req =>
		Ok(views.html.login(loginForm))
	}

	def loginPost = UserAction { implicit req =>
		Ok(views.html.login(loginForm))
	}

	val registerForm = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"mail" -> email,
			"pass" -> nonEmptyText(6)
		)(RegisterData.apply)(RegisterData.unapply)
	)

	def register = UserAction { implicit req =>
		Ok(views.html.register(registerForm))
	}

	def registerPost = UserAction.async { implicit req =>
		val form = registerForm.bindFromRequest
		form.fold(
			error => BadRequest(views.html.register(error)),
			data => {
				sqlu"""
					INSERT INTO users
					SET login = LOWER(${data.login}), email = ${data.mail}, password = HASHPASS(${data.pass}, ${data.login})
				""".run map { case _ =>
					Redirect("/login")
				} recover { case error =>
					val (field, message) = error match {
						case e: IntegrityViolation if e.contains("PRIMARY") => "login" -> "Username is already taken"
						case e: IntegrityViolation if e.contains("email") => "mail" -> "E-mail address is already taken"
						case e => "unknown" -> e.getMessage
					}
					BadRequest(views.html.register(form.withError(field, message)))
				}
			}
		)
	}
}
