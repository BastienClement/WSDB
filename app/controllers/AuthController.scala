package controllers

import com.google.inject.Inject
import controllers.Auth.{LoginData, RegisterData}
import controllers.mysql._
import play.api.data.Forms._
import play.api.data._
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._

object Auth {
	case class LoginData(login: String, pass: String)
	case class RegisterData(login: String, mail: String, pass: String)
}

class AuthController @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
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
				val login = data.login
				val pass = data.pass
				sqlu"""
					UPDATE users SET lastConnection = NOW()
					WHERE login = LOWER($login) AND password = HASHPASS(login, $pass)
					LIMIT 1
				""".run map {
					case count if count < 1 =>
						Unauthorized(views.html.login(form.withError("global", "Login name or password is invalid")))
					case _ =>
						Redirect(routes.CollectionController.index()).withSession(req.session + ("login" -> data.login))
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
				val login = data.login
				val mail = data.mail
				val pass = data.pass
				sqlu"""
					INSERT INTO users
					SET login = LOWER($login), email = $mail, password = HASHPASS($login, $pass)
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
		Redirect(routes.CollectionController.index()).withNewSession
	}
}
