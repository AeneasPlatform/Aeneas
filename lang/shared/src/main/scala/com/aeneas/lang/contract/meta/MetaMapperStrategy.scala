package com.aeneas.lang.contract.meta

import com.aeneas.lang.v1.compiler.Types.FINAL
import com.wavesplatform.protobuf.dapp.DAppMeta

private[meta] trait MetaMapperStrategy[V <: MetaVersion] {
  def toProto(data: List[List[FINAL]]): Either[String, DAppMeta]
  def fromProto(meta: DAppMeta): Either[String, List[List[FINAL]]]
}
