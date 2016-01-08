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
	//
	// --- Login ---
	//

	val loginForm = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"pass" -> nonEmptyText(6)
		)(LoginData.apply)(LoginData.unapply)
	)

	def login = Unauthenticated { implicit req =>
		Ok(views.html.login(loginForm))
	}

	def loginPost = Unauthenticated.async { implicit req =>
		val form = loginForm.bindFromRequest
		form.fold(
			error => BadRequest(views.html.login(error)),
			data => {
				sqlu"""
			      UPDATE users SET lastConnection = NOW()
			      WHERE login = LOWER(${data.login}) AND password = HASHPASS(login, ${data.pass})
					LIMIT 1
			   """.run map {
					case count if count < 1 =>
						Unauthorized(views.html.login(form.withError("global", "Login name or password is invalid")))
					case _ =>
						Redirect("/").withSession(req.session + ("login" -> data.login))
				}
			}
		)
	}

	//
	// --- Register ---
	//

	val registerForm = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"mail" -> email,
			"pass" -> nonEmptyText(6)
		)(RegisterData.apply)(RegisterData.unapply)
	)

	def register = Unauthenticated { implicit req =>
		Ok(views.html.register(registerForm))
	}

	def registerPost = Unauthenticated.async { implicit req =>
		val form = registerForm.bindFromRequest
		form.fold(
			error => BadRequest(views.html.register(error)),
			data => {
				sqlu"""
					INSERT INTO users
					SET login = LOWER(${data.login}), email = ${data.mail}, password = HASHPASS(LOWER(${data.login}), ${data.pass})
				""".run map { case _ =>
					Redirect("/login")
				} recover { case error =>
					val (field, message) = error match {
						case e: IntegrityViolation if e.contains("PRIMARY") => "login" -> "Username is already taken"
						case e: IntegrityViolation if e.contains("email") => "mail" -> "E-mail address is already taken"
						case e => "global" -> e.getMessage
					}
					BadRequest(views.html.register(form.withError(field, message)))
				}
			}
		)
	}

	//
	// --- Logout ---
	//

	def logout = Authenticated {
		Redirect("/").withNewSession
	}
}
