package com.aeneas.lang.directives

import com.aeneas.lang.ExecutionError
import fastparse._
import fastparse.Parsed.{Failure, Success}
import fastparse.MultiLineWhitespace._
import cats.implicits._

object DirectiveParser {

  val start = "{-#"
  val end   = "#-}"

  private def space[_: P]: P[Unit] =
    P(CharIn(" ", "\t", "\r", "\n").rep)

  private def directiveKeyP[_: P]: P[String] =
    P(CharIn("a-zA-Z0-9_"))
      .repX(1).!

  private def directiveValueP[_:P]: P[String] =
    P(CharIn("a-zA-Z0-9/\\.,"))
      .repX(1).!

  private def parser[_:P]: P[Either[ExecutionError, Directive]] =
    P(space ~ start ~ directiveKeyP ~ directiveValueP ~ end ~ space)
      .map {
        case (keyRaw, valueRaw) =>
          for {
            key   <- DirectiveKey.textMap.get(keyRaw).toRight(s"Illegal directive key $keyRaw")
            value <- key match {
              case k: PredefinedDirectiveKey => k.valueDic.textMap.get(valueRaw).toRight(s"Illegal directive value $valueRaw for key $keyRaw")
              case k: ArbitraryDirectiveKey  => Right(k.valueMapper(valueRaw))
            }
          } yield Directive(key, value)
      }

  def apply(input: String): Either[ExecutionError, List[Directive]] =
    input.split("\n")
      .filter(_.matches(s"\\s*\\$start.*$end\\s*"))
      .map(parse(_, parser(_)))
      .foldLeft(Map[DirectiveKey, Directive]().asRight[ExecutionError]) {
        case (err: Left[_, _], _)                                      => err
        case (_, _: Failure)                                           => Left(s"Directive $input has illegal format")
        case (_, Success(Left(err), _))                                => Left(err)
        case (Right(acc), Success(Right(d), _)) if acc.contains(d.key) => Left(s"Directive key ${d.key.text} is used more than once")
        case (Right(acc), Success(Right(d), _))                        => Right(acc + (d.key -> d))
      }
      .map(_.values.toList)

  case class ParseResult(keys: Set[DirectiveKey], directives: List[Directive])
}
