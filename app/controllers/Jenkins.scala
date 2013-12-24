package controllers

import play.api.mvc._
import models._
import play.api.libs.json._
import com.github.nscala_time.time.Imports._
import Writes._
import models.jenkins.RealJenkinsRepository
import scala.util.{Failure, Success}
import scalaj.http.HttpException

object Jenkins extends Controller with Secured {
  def forceBuild(pullRequestId: Option[Int], branchId: Option[String], fullCycle: Boolean) = IsAuthorized {
    implicit user =>
      request =>
        val maybeAction: Option[BuildAction] = (pullRequestId, branchId) match {
          case (Some(prId), None) => Some(PullRequestBuildAction(prId, fullCycle))
          case (None, Some(brId)) => Some(BranchBuildAction(brId, fullCycle))
          case _ => None
        }
        maybeAction match {
          case Some(buildAction) =>
            RealJenkinsRepository.forceBuild(buildAction) match {
              case Success(_) => Ok(Json.toJson(
                Build(-1, "this", Some("In progress"), "#", DateTime.now, BuildNode(-1, "this", "this", Some("In progress"), "#", None, DateTime.now))
              ))
              case Failure(e: HttpException) => BadRequest(e.message)
              case Failure(e) => InternalServerError("Something going wrong " + e.toString)
            }
          case None => BadRequest("There is no pullRequestId or branchId")
        }
  }
}
