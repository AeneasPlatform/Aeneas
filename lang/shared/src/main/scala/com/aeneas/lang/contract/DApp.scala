package com.aeneas.lang.contract

import com.aeneas.lang.contract.DApp.{CallableFunction, VerifierFunction}
import com.aeneas.lang.directives.values.StdLibVersion
import com.aeneas.lang.v1.compiler.CompilationError.Generic
import com.aeneas.lang.v1.compiler.Terms.DECLARATION
import com.aeneas.lang.v1.compiler.Types._
import com.aeneas.lang.v1.compiler.{CompilationError, Terms}
import com.aeneas.lang.v1.evaluator.ctx.impl.waves.Types
import com.wavesplatform.protobuf.dapp.DAppMeta

case class DApp(
    meta: DAppMeta,
    decs: List[DECLARATION],
    callableFuncs: List[CallableFunction],
    verifierFuncOpt: Option[VerifierFunction]
)

object DApp {

  sealed trait Annotation {
    def invocationArgName: String
    def dic(version: StdLibVersion): Map[String, FINAL]
  }
  object Annotation {
    def parse(name: String, args: List[String]): Either[CompilationError, Annotation] = {
      (name, args) match {
        case ("Verifier", s :: Nil) => Right(VerifierAnnotation(s))
        case ("Verifier", s :: xs)  => Left(Generic(0, 0, "Incorrect amount of bound args in Verifier, should be one, e.g. @Verifier(tx)"))
        case ("Callable", s :: Nil) => Right(CallableAnnotation(s))
        case ("Callable", s :: xs)  => Left(Generic(0, 0, "Incorrect amount of bound args in Callable, should be one, e.g. @Callable(inv)"))
        case _                      => Left(Generic(0, 0, "Annotation not recognized"))
      }
    }

    def validateAnnotationSet(l: List[Annotation]): Either[CompilationError, Unit] = {
      l match {
        case (v: VerifierAnnotation) :: Nil => Right(())
        case (c: CallableAnnotation) :: Nil => Right(())
        case _                              => Left(Generic(0, 0, "Unsupported annotation set"))
      }
    }
  }
  case class CallableAnnotation(invocationArgName: String) extends Annotation {
    override def dic(version: StdLibVersion): Map[String, FINAL] =
      Map(invocationArgName -> Types.invocationType(version))
  }

  case class VerifierAnnotation(invocationArgName: String) extends Annotation {
    override def dic(version: StdLibVersion): Map[String, FINAL] =
      Map(invocationArgName -> Types.verifierInput(version))
  }

  sealed trait AnnotatedFunction {
    def annotation: Annotation
    def u: Terms.FUNC
  }
  case class CallableFunction(override val annotation: CallableAnnotation, override val u: Terms.FUNC) extends AnnotatedFunction
  case class VerifierFunction(override val annotation: VerifierAnnotation, override val u: Terms.FUNC) extends AnnotatedFunction
}
