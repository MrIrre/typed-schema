package ru.tinkoff.tschema.example

import ru.tinkoff.tschema.finagle.Serve.Filter
import ru.tinkoff.tschema.finagle.{MkService, Rejection, Routed, Serve}
import ru.tinkoff.tschema.param.ParamSource.Query
import ru.tinkoff.tschema.swagger.{SwaggerMapper, _}
import syntax._
import ru.tinkoff.tschema.typeDSL._
import shapeless.{HList, Witness}
import cats.syntax.flatMap._
import cats.syntax.applicative._
import cats.syntax.semigroupk._
import cats.syntax.order._
import cats.instances.string._
import ru.tinkoff.tschema.common.Name
import cats.instances.string._
import ru.tinkoff.tschema.finagle.tethysInstances._
import Routed.{reject, uriParam}
import cats.SemigroupK
import com.twitter.finagle.http.{Request, Response}
import ru.tinkoff.tschema.param.Param

object VersionModule extends ExampleModule {
  def api = tagPrefix('versioned) |> (
    (version('v1) |> get[String]) <>
      (version('v2) |> get[Map[String, Int]]) <>
      (version("v2.1") |> get[Vector[String]])
  )

  object service {
    def v1     = "Ololo"
    def v2     = Map("Olol" -> 0)
    def `v2.1` = Vector("Olo", "lo")
  }

  val route = MkService[Http](api)(service)
  val swag  = MkSwagger(api)
}

final class version[v] extends DSLAtom

object version {
  def wrongVersion(shouldBe: String, passed: String) =
    Rejection.malformedParam("version", s"passed version $passed shouldBe: $shouldBe", Query)

  def apply[v](v: Witness.Aux[v]): version[v] :> Key[v] = new version[v] :> key(v)

  implicit def versionServe[v: Name, In <: HList]: Filter[version[v], Http, In] =
    Serve.checkCont[version[v], Http, In] { cnt =>
      val shouldBe = Name[v].string

      Routed.checkPath[Http, Response](Name[v].string, cnt) <+>
        (uriParam[Http, String]("version").flatMap { s =>
          reject[Http, Unit](wrongVersion(shouldBe, s)).whenA(s =!= shouldBe)
        } >> cnt)
    }

  implicit def versionSwagger[v: Name]: SwaggerMapper[version[v]] = SwaggerMapper[Prefix[v]].as[version[v]]
}
