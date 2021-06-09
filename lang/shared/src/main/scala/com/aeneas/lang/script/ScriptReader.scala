package com.aeneas.lang.script
import com.aeneas.lang.ValidationError.ScriptParseError
import com.aeneas.lang.contract.ContractSerDe
import com.aeneas.lang.directives.DirectiveDictionary
import com.aeneas.lang.directives.values._
import com.aeneas.lang.script.v1.ExprScript
import com.aeneas.lang.v1.{BaseGlobal, Serde}

object ScriptReader {

  private val Global: BaseGlobal = com.aeneas.lang.Global // Hack for IDEA

  val checksumLength = 4

  def fromBytes(bytes: Array[Byte]): Either[ScriptParseError, Script] = {
    val checkSum         = bytes.takeRight(checksumLength)
    val computedCheckSum = Global.secureHash(bytes.dropRight(checksumLength)).take(checksumLength)

    for {
      versionByte <- bytes.headOption.toRight(ScriptParseError("Can't parse empty script bytes"))
      a <- {
        val contentTypes   = DirectiveDictionary[ContentType].idMap
        val stdLibVersions = DirectiveDictionary[StdLibVersion].idMap
        versionByte match {
          case 0 =>
            if (bytes.length <= 2)
              Left(ScriptParseError(s"Illegal length of script: ${bytes.length}"))
            else if (!contentTypes.contains(bytes(1)))
              Left(ScriptParseError(s"Invalid content type of script: ${bytes(1)}"))
            else if (!stdLibVersions.contains(bytes(2)))
              Left(ScriptParseError(s"Invalid version of script: ${bytes(2)}"))
            else
              Right((contentTypes(bytes(1)), stdLibVersions(bytes(2)), 3))
          case v if !stdLibVersions.contains(v) => Left(ScriptParseError(s"Invalid version of script: $v"))
          case v                                => Right((Expression, stdLibVersions(v.toInt), 1))
        }
      }
      (scriptType, stdLibVersion, offset) = a
      scriptBytes                         = bytes.drop(offset).dropRight(checksumLength)

      _ <- Either.cond(java.util.Arrays.equals(checkSum, computedCheckSum), (), ScriptParseError("Invalid checksum"))
      s <- (scriptType match {
        case Expression | Library =>
          for {
            bytes <- Serde.deserialize(scriptBytes).map(_._1)
            s     <- ExprScript(stdLibVersion, bytes, checkSize = false)
          } yield s
        case DApp =>
          for {
            dapp <- ContractSerDe.deserialize(scriptBytes)
            s    <- ContractScript(stdLibVersion, dapp)
          } yield s
      }).left
        .map(ScriptParseError)
    } yield s
  }
}
