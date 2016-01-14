package models.services

import components.DefaultRegistry
import play.api.Play
import play.api.Play.current
import rx.lang.scala.{Observable, Subscription}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

object CacheService {

  val registry = DefaultRegistry

  val jenkinsDataPath: String = registry.config.jenkinsDataPath

  val githubInterval = Play.configuration.getMilliseconds("github.cache.interval").getOrElse(600000L).milliseconds
  val jenkinsInterval = registry.config.jenkinsInterval


  def start = {

    val jenkinsSubscription: Future[Subscription] = Future {
      updateBuilds(Nil)
    }.map(_ => subscribeToJenkins)
    val githubSubscription = subscribeToGithub

    Subscription {
      githubSubscription.unsubscribe()
      jenkinsSubscription.onSuccess { case x => x.unsubscribe() }
    }
  }

  def subscribeToJenkins: Subscription = {
    val buildObservable = registry.buildWatcher.start

    buildObservable
      .buffer(jenkinsInterval)
      .map(_.distinct)
      .subscribe(buildNames => Try {
        if (buildNames.nonEmpty) {
          updateBuilds(buildNames)
        }

      }.recover {
        case e => play.Logger.error("Error in jenkinsSubscription", e)
      },
        error => {
          play.Logger.error("Error in jenkinsSubscription", error)
        })

  }

  def updateBuilds(updatedBuildNames: Seq[String]) {
    val existingBuilds = registry.buildRepository.getBuilds.toList
    play.Logger.info(s"existingBuilds: ${existingBuilds.length}")

    val updatedBuilds = registry.jenkinsService.getUpdatedBuilds(existingBuilds, updatedBuildNames)
    play.Logger.info(s"buildToUpdate: ${updatedBuilds.length}")

    for (updatedBuild <- updatedBuilds) {
      registry.buildRepository.update(updatedBuild)
      registry.buildRerun.rerunFailedParts(updatedBuild)
    }

    registry.notificationService.notifyAboutBuilds(updatedBuilds.toIterator)
  }

  def subscribeToGithub: Subscription = {
    Observable.timer(0 seconds, githubInterval)
      .map(_ => Try {
        registry.branchService.getBranches
      })
      .subscribe({
        case Success(data) =>
          val branches = registry.branchRepository.getBranches

          Try {
            branches
              .filter(b => !data.exists(_.name == b.name))
              .foreach(branch => {
                registry.branchRepository.remove(branch)
                registry.buildRepository.removeAll(branch)
              })
          }


          Try {
            data.foreach(branch => registry.branchRepository.update(branch))
          }

        case Failure(e) => play.Logger.error("Error", e)
      },
        error => {
          play.Logger.error("Error in githubSubscription", error)
        })
  }
}
