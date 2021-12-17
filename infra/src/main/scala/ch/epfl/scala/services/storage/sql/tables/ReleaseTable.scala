package ch.epfl.scala.services.storage.sql.tables

import java.time.Instant

import ch.epfl.scala.index.model.release.MavenReference
import ch.epfl.scala.index.model.release.Platform
import ch.epfl.scala.index.newModel.NewProject
import ch.epfl.scala.index.newModel.NewRelease
import ch.epfl.scala.index.newModel.NewRelease.ArtifactName
import ch.epfl.scala.utils.DoobieUtils.Fragments._
import ch.epfl.scala.utils.DoobieUtils.Mappings._
import ch.epfl.scala.utils.DoobieUtils._
import doobie._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.update.Update

object ReleaseTable {
  private val _ = documentationLinksMeta
  private[sql] val table = "releases"
  private[sql] val fields = Seq(
    "groupId",
    "artifactId",
    "version",
    "organization",
    "repository",
    "artifact",
    "platform",
    "description",
    "released_at",
    "resolver",
    "licenses",
    "isNonStandardLib"
  )
  private val tableFr: Fragment = Fragment.const0(table)
  private val fieldsFr: Fragment = Fragment.const0(fields.mkString(", "))

  private def values(r: NewRelease): Fragment =
    fr0"${r.maven.groupId}, ${r.maven.artifactId}, ${r.version}, ${r.organization}, ${r.repository}," ++
      fr0" ${r.artifactName}, ${r.platform}, ${r.description}, ${r.releasedAt}, ${r.resolver}, ${r.licenses}, ${r.isNonStandardLib}"

  val insert: Update[NewRelease] = insertRequest(table, fields)

  def indexedReleased(): Query0[Long] =
    buildSelect(tableFr, fr0"count(*)").query[Long]

  val updateProjectRef: Update[(NewProject.Reference, MavenReference)] =
    Update[(NewProject.Reference, MavenReference)](
      s"UPDATE $table SET organization=?, repository=? WHERE groupId=? AND artifactId=? AND version=?"
    )

  def selectReleases(ref: NewProject.Reference): Query0[NewRelease] =
    buildSelect(tableFr, fr0"*", whereRef(ref)).query[NewRelease]

  def selectReleases(
      ref: NewProject.Reference,
      artifactName: ArtifactName
  ): doobie.Query0[NewRelease] =
    buildSelect(
      tableFr,
      fr0"*",
      whereRef(ref) ++ fr0" AND artifact=$artifactName"
    ).query[NewRelease]

  def selectPlatform(): Query0[
    (NewProject.Organization, NewProject.Repository, Platform)
  ] =
    buildSelect(
      tableFr,
      fr0"organization, repository, platform",
      fr0"GROUP BY organization, repository, platform"
    ).query[(NewProject.Organization, NewProject.Repository, Platform)]

  def findOldestReleasesPerProjectReference(): Query0[(Instant, NewProject.Reference)] =
    buildSelect(
      tableFr,
      fr0"min(released_at) as oldest_release, organization, repository",
      fr0"where released_at IS NOT NULL" ++ space ++ fr0"group by organization, repository"
    ).query[(Instant, NewProject.Reference)]

}
