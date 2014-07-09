/*
 * Copyright (C) 2009-2013 Typesafe Inc. <http://www.typesafe.com>
 */
package play.core

import java.io._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{ Try, Success, Failure }

import play.api._
import play.api.mvc._
import scala.concurrent.Future
import scala.util.control.NonFatal

/**
 * provides source code to be displayed on error pages
 */
trait SourceMapper {

  def sourceOf(className: String, line: Option[Int] = None): Option[(File, Option[Int])]

  def sourceFor(e: Throwable): Option[(File, Option[Int])] = {
    e.getStackTrace.find(element => sourceOf(element.getClassName).isDefined).flatMap { interestingStackTrace =>
      sourceOf(interestingStackTrace.getClassName, Option(interestingStackTrace.getLineNumber))
    }
  }

}

trait DevSettings {
  def devSettings: Map[String, String]
}

/**
 * Provides information about a Play Application running inside a Play server.
 */
trait ApplicationProvider {
  def path: File
  def get: Try[Application]
  final def getProvided(): CurrentApplication = CurrentApplication(get)
  def handleWebCommand(tryApp: Try[Application], requestHeader: play.api.mvc.RequestHeader): Option[Result] = None
}


case class CurrentApplication(
  tryApplication: Try[Application]
) {
  def appDefined: Boolean = tryApplication.isSuccess

  val global: GlobalSettings = tryApplication.map(_.global).getOrElse(DefaultGlobal)

  /** Error handling to use during execution of a handler (e.g. an action) */
  def handleHandlerError(rh: RequestHeader, t: Throwable): Future[Result] = {
    tryApplication match {
      case Success(app) => app.handleError(rh, t)
      case Failure(_) => global.onError(rh, t)
    }
  }
}

trait HandleWebCommandSupport {
  def handleWebCommand(request: play.api.mvc.RequestHeader, buildLink: play.core.BuildLink, path: java.io.File): Option[Result]
}

/**
 * creates and initializes an Application
 * @param applicationPath location of an Application
 */
class StaticApplication(applicationPath: File) extends ApplicationProvider {

  val application = new DefaultApplication(applicationPath, this.getClass.getClassLoader, None, Mode.Prod)

  Play.start(application)

  def get = Success(application)
  def path = applicationPath
}

/**
 * wraps and starts a fake application (used in tests)
 * @param application fake Application
 */
class TestApplication(application: Application) extends ApplicationProvider {

  Play.start(application)

  def get = Success(application)
  def path = application.path
}

/**
 * Represents an application that can be reloaded in Dev Mode.
 */
class ReloadableApplication(buildLink: BuildLink, buildDocHandler: BuildDocHandler) extends ApplicationProvider {

  // Use plain Java call here in case of scala classloader mess
  {
    if (System.getProperty("play.debug.classpath") == "true") {
      System.out.println("\n---- Current ClassLoader ----\n")
      System.out.println(this.getClass.getClassLoader)
      System.out.println("\n---- The where is Scala? test ----\n")
      System.out.println(this.getClass.getClassLoader.getResource("scala/Predef$.class"))
    }
  }

  lazy val path = buildLink.projectPath

  println(play.utils.Colors.magenta("--- (Running the application from SBT, auto-reloading is enabled) ---"))
  println()

  var lastState: Try[Application] = Failure(new PlayException("Not initialized", "?"))

  def get = {

    synchronized {

      // Let's load the application on another thread
      // as we are now on the Netty IO thread.
      //
      // Because we are on DEV mode here, it doesn't really matter
      // but it's more coherent with the way it works in PROD mode.
      implicit val ec = play.core.Execution.internalContext
      Await.result(scala.concurrent.Future {

        val reloaded = buildLink.reload match {
          case NonFatal(t) => Failure(t)
          case cl: ClassLoader => Success(Some(cl))
          case null => Success(None)
        }

        reloaded.flatMap { maybeClassLoader =>

          val maybeApplication: Option[Try[Application]] = maybeClassLoader.map { projectClassloader =>
            try {

              if (lastState.isSuccess) {
                println()
                println(play.utils.Colors.magenta("--- (RELOAD) ---"))
                println()
              }

              val reloadable = this

              // First, stop the old application if it exists
              Play.stop()

              val newApplication = new DefaultApplication(reloadable.path, projectClassloader, Some(new SourceMapper {
                def sourceOf(className: String, line: Option[Int]) = {
                  Option(buildLink.findSource(className, line.map(_.asInstanceOf[java.lang.Integer]).orNull)).flatMap {
                    case Array(file: java.io.File, null) => Some((file, None))
                    case Array(file: java.io.File, line: java.lang.Integer) => Some((file, Some(line)))
                    case _ => None
                  }
                }
              }), Mode.Dev) with DevSettings {
                import scala.collection.JavaConverters._
                lazy val devSettings: Map[String, String] = buildLink.settings.asScala.toMap
              }

              Play.start(newApplication)

              Success(newApplication)
            } catch {
              case e: PlayException => {
                lastState = Failure(e)
                lastState
              }
              case NonFatal(e) => {
                lastState = Failure(UnexpectedException(unexpected = Some(e)))
                lastState
              }
              case e: LinkageError => {
                lastState = Failure(UnexpectedException(unexpected = Some(e)))
                lastState
              }
            }
          }

          maybeApplication.flatMap(_.toOption).foreach { app =>
            lastState = Success(app)
          }

          maybeApplication.getOrElse(lastState)
        }

      }, Duration.Inf)
    }
  }

  override def handleWebCommand(tryApp: Try[Application], request: play.api.mvc.RequestHeader): Option[Result] = {

    buildDocHandler.maybeHandleDocRequest(request).asInstanceOf[Option[Result]].orElse(
      for {
        app <- tryApp.toOption
        result <- app.plugins.foldLeft(Option.empty[Result]) {
          case (None, plugin: HandleWebCommandSupport) => plugin.handleWebCommand(request, buildLink, path)
          case (result, _) => result
        }
      } yield result
    )

  }


}

