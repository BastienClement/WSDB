package controllers

import play.api.data.Form
import play.api.data.Forms._

/**
  * Collection of Form object and associated data case-class across the application
  */
object Forms {
	/**
	  * New deck creation
	  */
	case class NewDeckData(name: String)
	val NewDeck = Form(
		mapping(
			"name" -> nonEmptyText(1, 32)
		)(NewDeckData.apply)(NewDeckData.unapply)
	)

	/**
	  * Login
	  */
	case class LoginData(login: String, pass: String)
	val Login = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"pass" -> nonEmptyText(6)
		)(LoginData.apply)(LoginData.unapply)
	)

	/**
	  * Register
	  */
	case class RegisterData(login: String, mail: String, pass: String)
	val Register = Form(
		mapping(
			"login" -> nonEmptyText(3, 32),
			"mail" -> email,
			"pass" -> nonEmptyText(6)
		)(RegisterData.apply)(RegisterData.unapply)
	)
}
