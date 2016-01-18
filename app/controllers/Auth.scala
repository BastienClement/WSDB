package controllers

import com.google.inject.Inject
import models.Query
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._

/**
  * Controller responsible for handling authentication-related requests
  */
class Auth @Inject()(val messagesApi: MessagesApi) extends Controller with I18nSupport {
	/**
	  * Login page
	  */
	def login = Unauthenticated { implicit req =>
		Ok(views.html.login(Forms.Login))
	}

	/**
	  * Handle login form submission
	  */
	def loginPost = Unauthenticated.async { implicit req =>
		val form = Forms.Login.bindFromRequest
		form.fold(
			error => BadRequest(views.html.login(error)),
			{ case Forms.LoginData(login, pass) =>
				Query.login(login, pass).map {
					case false =>
						val error_form = form.withError("global", "Login name or password is invalid")
						Unauthorized(views.html.login(error_form))
					case true =>
						Redirect(routes.Collection.index()).addingToSession("login" -> login)
				}
			}
		)
	}

	/**
	  * Register page
	  */
	def register = Unauthenticated { implicit req =>
		Ok(views.html.register(Forms.Register))
	}

	/**
	  * Handle register form submission
	  */
	def registerPost = Unauthenticated.async { implicit req =>
		val form = Forms.Register.bindFromRequest
		form.fold(
			error => BadRequest(views.html.register(error)),
			{ case Forms.RegisterData(login, mail, pass) =>
				Query.register(login, mail, pass) map { case _ =>
					Redirect(routes.Auth.login())
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

	/**
	  * Logout
	  */
	def logout = Authenticated {
		Redirect(routes.Collection.index()).withNewSession
	}
}
