import java.sql.SQLIntegrityConstraintViolationException
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json.Json
import play.api.mvc._
import play.api.mvc.Results.Forbidden
import scala.concurrent.Future
import scala.language.implicitConversions
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import slick.driver.MySQLDriver.api._

package object controllers {
	val DB = DatabaseConfigProvider.get[JdbcProfile](Play.current).db
	val mysql = slick.driver.MySQLDriver.api

	/** Implicitly use the global ExecutionContext */
	implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

	/** Adds the .contains method on Throwables to check if message contains a given string */
	implicit class ThrowableMessageContains[R](val t: Throwable) extends AnyVal {
		@inline def contains(s: CharSequence) = t.getMessage.contains(s)
	}

	/** Adds the .run method directly on DBIOAction objects */
	implicit class DBIOActionExecutor[R](val a: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline def run = DB.run(a)
	}

	/** Implicitly executes DBIOAction if context require a Future[R] */
	implicit def DBIOActionImplicitExecutor[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = DB.run(a)

	/** Implicitly wrap Result in a Future if async is expected */
	implicit def FutureResult(res: Result): Future[Result] = Future.successful(res)

	/** Connected user **/
	case class User(login: String, email: String)

	/** A request with user information */
	class UserRequest[A](val user: User, val authenticated: Boolean, request: Request[A]) extends WrappedRequest[A](request)

	/** Authenticated action */
	object UserAction extends ActionBuilder[UserRequest] with ActionTransformer[Request, UserRequest] {
		def transform[A](request: Request[A]) = request.session.get("username") match {
			case Some(username) =>
				val query = sql"SELECT login, email FROM users WHERE active = 1 AND login = $username".as[(String, String)]
				query.head.map(User.tupled).run.map(user => new UserRequest(user, true, request))
			case None => Future.successful(new UserRequest(null, false, request))
		}
	}

	object AuthenticatedFilter extends ActionFilter[UserRequest] {
		def filter[A](request: UserRequest[A]) = Future.successful {
			if (!request.authenticated) Some(Forbidden)
			else None
		}
	}

	val Authenticated = UserAction andThen AuthenticatedFilter
	val ApiAuthenticated = UserAction andThen new ActionFilter[UserRequest] {
		def filter[A](request: UserRequest[A]) = Future.successful {
			if (!request.authenticated) Some(Forbidden(Json.obj("error" -> "Forbidden")))
			else None
		}
	}

	/** Alias for long SQL exception names */
	type IntegrityViolation = SQLIntegrityConstraintViolationException
}
