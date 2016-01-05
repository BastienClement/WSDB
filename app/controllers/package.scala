import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.Future
import scala.language.implicitConversions
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile

package object controllers {
	val DB = DatabaseConfigProvider.get[JdbcProfile](Play.current).db
	val mysql = slick.driver.MySQLDriver.api

	/** Implicitly use the global ExecutionContext */
	implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

	/** Adds the .run method directly on DBIOAction objects */
	implicit class DBIOActionExecutor[R](val a: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline def run = DB.run(a)
	}

	/** Implicitly executes DBIOAction if context require a Future[R] */
	implicit def DBIOActionImplicitExecutor[R](a: DBIOAction[R, NoStream, Nothing]): Future[R] = DB.run(a)
}
