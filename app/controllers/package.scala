import java.sql.SQLIntegrityConstraintViolationException
import org.apache.commons.lang3.exception.ExceptionUtils
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.mvc.Results._
import play.api.mvc._
import scala.collection.mutable
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{higherKinds, implicitConversions}
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile

package object controllers {
	val DB = DatabaseConfigProvider.get[JdbcProfile](Play.current).db
	val mysql = slick.driver.MySQLDriver.api

	/** Implicitly use the global ExecutionContext */
	implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

	/**
	  * Adds the .contains method on Throwables to check if message contains a given string.
	  * Used to check SQL error messages.
	  */
	implicit class ThrowableMessageContains[R](val t: Throwable) extends AnyVal {
		@inline def contains(s: CharSequence) = t.getMessage.contains(s)
	}

	/** Adds the .run method directly on DBIOAction objects */
	implicit class DBIOActionExecutor[R](val a: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline def run = DB.run(a)
	}

	/** Adds utility functions on String */
	implicit class WSStringUtils[R](val str: String) extends AnyVal {
		@inline def toOptInt: Option[Int] = {
			try {
				Some(str.toInt)
			} catch {
				case _: Throwable => None
			}
		}
	}

	/** Implicitly executes DBIOAction[R, _, _] if context require a Future[R] */
	implicit def DBIOActionImplicitExecutor[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = DB.run(a)

	/**
	  * Implicitly wrap Result in a Future if async is expected.
	  * Often, we need Action.async because the result is built from database data but
	  * if some pre-condition fails, the error result can be constructed synchronously.
	  */
	implicit def FutureResult(res: Result): Future[Result] = Future.successful(res)

	/**
	  * Add a .pack() method on Vector[T] to build a Vector[Vector[T]] based on a hash function.
	  * Elements from the original Vector with the same hash are grouped in the same sub-vector.
	  * Used to group rows from JOIN query together.
	  * In contrast to .groupBy() this method preserve ordering and have a different return type.
	  */
	implicit class VectorPacker[T, S[_] <: Seq[_]](val vector: Seq[T]) extends AnyVal {
		type Hash[K] = (T) => K

		/**
		  * Compute and group elements by hash.
		  * Also return an in-order sequence of key.
		  */
		private def process[K](hash: Hash[K]): (Vector[K], mutable.Map[K, Vector[T]]) = {
			var order = Vector[K]()
			val packs = mutable.Map[K, Vector[T]]().withDefault { key =>
				order :+= key
				Vector.empty
			}
			for (item <- vector) {
				val key = hash(item)
				packs(key) :+= item
			}
			(order, packs)
		}

		/**
		  * Packs elements with the same hashing value together.
		  */
		def pack[K](hash: Hash[K]): Vector[Vector[T]] = {
			val (order, packs) = process(hash)
			for (key <- order) yield packs(key)
		}

		/**
		  * Packs elements with the same hashing value together.
		  * Also store the hash value with the elements list.
		  */
		def packWithKey[K](hash: Hash[K]): Vector[(K, Vector[T])] = {
			val (order, packs) = process(hash)
			for (key <- order) yield (key, packs(key))
		}
	}

	/** Connected user **/
	case class User(name: String, mail: String, disable_tips: Boolean)

	/** A request with user information */
	class UserRequest[A](val optUser: Option[User], request: Request[A])
		extends WrappedRequest[A](request) {
		val user = optUser.orNull
		val authenticated = optUser.isDefined

		lazy val deck_id = request.session.get("deck").map(_.toInt)
		lazy val deck = {
			val future_deck = deck_id match {
				case Some(id) if optUser.isDefined =>
					models.Query.deckData(id, user.name).map(Some(_)).recover { case e =>
						deck_failed = true
						None
					}
				case _ =>
					Future.successful(None)
			}
			Await.result(future_deck, 1.second)
		}

		private[this] var deck_failed = false
		def hasDeckFailed = deck_failed
	}

	/** Authenticated action */
	object UserAction extends ActionBuilder[UserRequest] {
		def transform[A](request: Request[A]) = {
			for {
				user <- request.session.get("login") match {
					case Some(login) => models.Query.user(login).map(_.map(User.tupled))
					case _ => Future.successful(None)
				}
			} yield {
				new UserRequest(user, request)
			}
		}

		override def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[Result]) = {
			transform(request).flatMap { implicit req =>
				(try {
					for {
						res <- block(req)
					} yield {
						if (req.hasDeckFailed) res.removingFromSession("deck")
						else res
					}
				} catch {
					case e: Throwable => Future.failed(e)
				}).recover { case error =>
					val title = "Fatal Exception"
					val msg = error match {
						case e: NoSuchElementException => "The element you requested does not exist in the database."
						case _ => error.getMessage
					}
					val trace = ExceptionUtils.getStackFrames(error).mkString("\n")
					BadRequest(views.html.error(title, msg, trace))
				}
			}
		}
	}

	/** Only allow authenticated users to access the action */
	val Authenticated = UserAction andThen new ActionFilter[UserRequest] {
		def filter[A](request: UserRequest[A]) = Future.successful {
			if (!request.authenticated) {
				val error = views.html.error(
					"Forbidden",
					"You are not allowed to access this page without authentication.",
					request.method + " " + request.uri)(request)
				Some(Forbidden(error))
			} else {
				None
			}
		}
	}

	/** Only allow un-authenticated users to access the action */
	val Unauthenticated = UserAction andThen new ActionFilter[UserRequest] {
		def filter[A](request: UserRequest[A]) = Future.successful {
			if (request.authenticated) Some(Redirect(routes.Collection.index()))
			else None
		}
	}

	/** Alias for long SQL exception names */
	type IntegrityViolation = SQLIntegrityConstraintViolationException
}
