package scaladex.infra.storage.sql

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import org.scalatest.funspec.AsyncFunSpec
import org.scalatest.matchers.should.Matchers
import scaladex.core.model.Artifact
import scaladex.core.model.ArtifactDependency
import scaladex.core.model.GithubStatus
import scaladex.core.model.Project
import scaladex.core.model.ProjectDependency
import scaladex.core.util.ScalaExtensions._

class SqlDatabaseTests extends AsyncFunSpec with BaseDatabaseSuite with Matchers {

  val executorService: ExecutorService = Executors.newFixedThreadPool(1)
  override implicit val executionContext: ExecutionContext =
    ExecutionContext.fromExecutorService(executorService)

  import scaladex.core.test.Values._

  it("insert release and its dependencies") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      project <- database.findProject(Cats.reference)
      releases <- database.findReleases(Cats.reference)
    } yield {
      project should not be empty
      releases should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`)
    }
  }

  it("should get all project statuses") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertRelease(PlayJsonExtra.artifact, Seq.empty, now)
      projectStatuses <- database.getAllProjectStatuses()
    } yield projectStatuses.keys should contain theSameElementsAs Seq(PlayJsonExtra.reference, Cats.reference)
  }

  it("should get all projects statuses") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.updateGithubInfoAndStatus(Cats.reference, Cats.githubInfo, ok)
      _ <- database.insertRelease(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, ok)
      _ <- database.updateProjectSettings(Scalafix.reference, Scalafix.settings)
      allProjects <- database.getAllProjects()
    } yield allProjects should contain theSameElementsAs Seq(Cats.project, Scalafix.project)
  }

  it("should update artifacts") {
    val newRef = Project.Reference.from("kindlevel", "dogs")
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
      _ <- database.updateReleases(Seq(Cats.`core_3:2.6.1`, Cats.core_sjs1_3), newRef)
      oldReleases <- database.findReleases(Cats.reference)
      movedReleases <- database.findReleases(newRef)
    } yield {
      oldReleases shouldBe empty
      movedReleases should contain theSameElementsAs Seq(
        Cats.`core_3:2.6.1`.copy(projectRef = newRef),
        Cats.core_sjs1_3.copy(projectRef = newRef)
      )
    }
  }

  it("should update github status") {
    val failed = GithubStatus.Failed(now, 405, "Unauthorized")
    for {
      _ <- database.insertRelease(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, ok)
      _ <- database.updateGithubStatus(Scalafix.reference, failed)
      scalafix <- database.findProject(Scalafix.reference)
    } yield scalafix.get.githubStatus shouldBe failed
  }

  it("should find artifacts by name") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
      artifacts <- database.findReleases(Cats.reference, Cats.`core_3:2.6.1`.artifactName)
    } yield artifacts should contain theSameElementsAs Seq(Cats.`core_3:2.6.1`, Cats.core_sjs1_3)
  }

  it("should count projects, artifacts, dependencies, github infos and data forms") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
      _ <- database.insertRelease(Scalafix.artifact, Seq.empty, now)
      _ <- database.insertRelease(PlayJsonExtra.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      _ <- database.updateProjectSettings(Scalafix.reference, Scalafix.settings)
      projects <- database.countProjects()
      artifacts <- database.countArtifacts()
      dependencies <- database.countDependencies()
      githubInfos <- database.countGithubInfo()
      settings <- database.countProjectSettings()
    } yield {
      projects shouldBe 3L
      artifacts shouldBe 4L
      dependencies shouldBe 3L
      githubInfos shouldBe 1L
      settings shouldBe 1L
    }
  }

  it("should find directDependencies") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertRelease(Cats.kernel_3, Seq.empty, now)
      directDependencies <- database.findDirectDependencies(Cats.`core_3:2.6.1`)
    } yield directDependencies.map(_.target) should contain theSameElementsAs List(Some(Cats.kernel_3), None, None)
  }

  it("should find reverseDependencies") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Cats.dependencies, now)
      _ <- database.insertRelease(Cats.kernel_3, Seq.empty, now)
      reverseDependencies <- database.findReverseDependencies(Cats.kernel_3)
    } yield reverseDependencies.map(_.source) should contain theSameElementsAs List(Cats.`core_3:2.6.1`)
  }

  it("should get all topics") {
    for {
      _ <- database.insertRelease(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      res <- database.getAllTopics()
    } yield res should contain theSameElementsAs Scalafix.githubInfo.topics
  }

  it("should get all platforms") {
    for {
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Seq.empty, now)
      _ <- database.insertRelease(Cats.core_native04_213, Seq.empty, now)
      _ <- database.insertRelease(Cats.core_sjs1_3, Seq.empty, now)
      _ <- database.insertRelease(Cats.core_sjs06_213, Seq.empty, now)
      res <- database.getAllPlatforms()
    } yield res(Cats.reference) should contain theSameElementsAs Set(
      Cats.`core_3:2.6.1`.platform,
      Cats.core_native04_213.platform,
      Cats.core_sjs1_3.platform,
      Cats.core_sjs06_213.platform
    )
  }

  it("should get most dependent project") {
    val releases: Map[Artifact, Seq[ArtifactDependency]] = Map(
      Cats.`core_3:2.6.1` -> Seq(
        ArtifactDependency(
          source = Cats.`core_3:2.6.1`.mavenReference,
          target = Artifact.MavenReference("fake", "fake_3", "version"),
          scope = "compile"
        )
      ), // first case: on a artifact that doesn't have a corresponding release
      Cats.kernel_3 -> Seq(
        ArtifactDependency(
          source = Cats.kernel_3.mavenReference,
          target = Cats.`core_3:2.6.1`.mavenReference,
          "compile"
        )
      ), // depends on it self
      Scalafix.artifact -> Cats.dependencies.map(
        _.copy(source = Scalafix.artifact.mavenReference)
      ), // dependencies contains two cats releases
      Cats.laws_3 -> Seq(), // doesn't depend on anything
      PlayJsonExtra.artifact -> Seq(
        ArtifactDependency(
          source = PlayJsonExtra.artifact.mavenReference,
          target = Scalafix.artifact.mavenReference,
          "compile"
        )
      )
    )
    for {
      _ <- releases.map { case (release, dependencies) => database.insertRelease(release, dependencies, now) }.sequence
      projectDependencies <- database.computeProjectDependencies()
      _ <- database.insertProjectDependencies(projectDependencies)
      catsInverseDependencies <- database.countInverseProjectDependencies(Cats.reference)
      mostDependentProjects <- database.getMostDependentUponProject(10)
    } yield {
      projectDependencies shouldBe Seq(
        ProjectDependency(Scalafix.reference, Cats.reference),
        ProjectDependency(Cats.reference, Cats.reference),
        ProjectDependency(PlayJsonExtra.reference, Scalafix.reference)
      )
      mostDependentProjects.map { case (project, deps) => (project.reference, deps) } shouldBe List(
        Cats.reference -> 2,
        Scalafix.reference -> 1
      )
      catsInverseDependencies shouldBe 2
    }
  }
  it("should update creation date and get latest project") {
    for {
      _ <- database.insertRelease(Scalafix.artifact, Seq.empty, now)
      _ <- database.insertRelease(Cats.`core_3:2.6.1`, Seq.empty, now)
      _ <- database.insertRelease(PlayJsonExtra.artifact, Seq.empty, now)
      creationDates <- database.computeAllProjectsCreationDate()
      _ <- creationDates.mapSync { case (creationDate, ref) => database.updateProjectCreationDate(ref, creationDate) }
      latestProject <- database.getLatestProjects(2)
    } yield (latestProject.map(p => p.reference -> p.creationDate) should contain).theSameElementsInOrderAs(
      Seq(
        Cats.reference -> Cats.`core_3:2.6.1`.releaseDate,
        Scalafix.reference -> Scalafix.artifact.releaseDate
      )
    )
  }
  it("should createMovedProject") {
    val destination = Project.Reference.from("scala", "fix")
    val moved = GithubStatus.Moved(now, destination)
    for {
      _ <- database.insertRelease(Scalafix.artifact, Seq.empty, now)
      _ <- database.updateGithubInfoAndStatus(Scalafix.reference, Scalafix.githubInfo, GithubStatus.Ok(now))
      _ <- database.moveProject(Scalafix.reference, Scalafix.githubInfo, moved)
      newProject <- database.findProject(destination)
      oldProject <- database.findProject(Scalafix.reference)
    } yield {
      oldProject.get.githubStatus shouldBe moved
      newProject.get.reference shouldBe destination
      newProject.get.githubStatus shouldBe GithubStatus.Ok(now)
    }
  }
}
