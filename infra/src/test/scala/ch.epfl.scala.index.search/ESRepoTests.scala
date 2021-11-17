package ch.epfl.scala.index.search

import scala.concurrent.ExecutionContext

import ch.epfl.scala.index.Values
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.model.release.Scala3Version
import ch.epfl.scala.index.model.release.ScalaVersion
import ch.epfl.scala.search.SearchParams
import org.scalatest._
import org.scalatest.funsuite.AsyncFunSuite
import org.scalatest.matchers.should.Matchers

class ESRepoTests extends AsyncFunSuite with Matchers with BeforeAndAfterAll {
  implicit override val executionContext: ExecutionContext =
    ExecutionContext.global

  private val searchEngine = ESRepo.open()

  override protected def beforeAll(): Unit = {
    searchEngine.waitUntilReady()
    searchEngine.reset()
  }

  override protected def afterAll(): Unit =
    searchEngine.close()

  import Values._

  test("match for cats with scala3") {
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      page <- searchEngine.find(SearchParams(queryString = "cats"))
    } yield page.items.map(_.document) should contain theSameElementsAs List(Cats.projectDocument)
  }

  test("search for cats_3") {
    val params = SearchParams(
      queryString = "cats",
      targetFiltering = Some(Platform.ScalaJvm(Scala3Version.`3`))
    )
    searchEngine.find(params).map { page =>
      page.items.map(_.document) should contain theSameElementsAs List(Cats.projectDocument)
    }
  }

  test("sort by dependent, created, stars, forks, and contributors") {
    val params = SearchParams(queryString = "*")
    val catsFirst = Seq(Cats.projectDocument, Scalafix.projectDocument)
    val scalafixFirst = Seq(Scalafix.projectDocument, Cats.projectDocument)
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.insert(Scalafix.projectDocument)
      _ <- searchEngine.refresh()
      byDependent <- searchEngine.find(params.copy(sorting = Some("dependent")))
      byCreated <- searchEngine.find(params.copy(sorting = Some("created")))
      byStars <- searchEngine.find(params.copy(sorting = Some("stars")))
      byForks <- searchEngine.find(params.copy(sorting = Some("forks")))
      byContributors <- searchEngine.find(params.copy(sorting = Some("contributors")))
    } yield {
      byDependent.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byCreated.items.map(_.document) should contain theSameElementsInOrderAs scalafixFirst
      byStars.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byForks.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
      byContributors.items.map(_.document) should contain theSameElementsInOrderAs catsFirst
    }
  }

  test("contributing search") {
    val expected = Seq(Cats.issueAboutFoo)
    val params = SearchParams("foo", contributingSearch = true)
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      hits <- searchEngine.find(params)
    } yield hits.items.flatMap(_.beginnerIssueHits) should contain theSameElementsAs expected
  }

  test("get Scala versions") {
    val expected = Seq(ScalaVersion.`2.13`.family -> 1L, Scala3Version.`3`.family -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaVersions <- searchEngine.getScalaVersions(params)
    } yield scalaVersions should contain theSameElementsAs expected
  }

  test("get Scala.js versions") {
    val expected = Seq(Platform.ScalaJs.`1.x` -> 1L, Platform.ScalaJs.`0.6` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaJsVersions <- searchEngine.getScalaJsVersions(params)
    } yield scalaJsVersions should contain theSameElementsAs expected
  }

  test("get Scala Native versions") {
    val expected = Seq(Platform.ScalaNative.`0.4` -> 1L)
    val params = SearchParams(queryString = "cats")
    for {
      _ <- searchEngine.insert(Cats.projectDocument)
      _ <- searchEngine.refresh()
      scalaNativeVersions <- searchEngine.getScalaNativeVersions(params)
    } yield scalaNativeVersions should contain theSameElementsAs expected
  }
}