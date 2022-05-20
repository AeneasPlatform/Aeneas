package com.aeneas.database

import com.aeneas.common.state.ByteStr
import org.iq80.leveldb.WriteBatch

import scala.collection.mutable

class SortedBatch extends WriteBatch {
  val addedEntries: mutable.Map[ByteStr, Array[Byte]] = mutable.TreeMap[ByteStr, Array[Byte]]()
  val deletedEntries: mutable.Set[ByteStr]            = mutable.TreeSet[ByteStr]()

  override def put(bytes: Array[Byte], bytes1: Array[Byte]): WriteBatch = {
    val k = ByteStr(bytes)
    addedEntries.put(k, bytes1)
    deletedEntries.remove(k)
    this
  }

  override def delete(bytes: Array[Byte]): WriteBatch = {
    val k = ByteStr(bytes)
    addedEntries.remove(k)
    deletedEntries.add(k)
    this
  }

  override def close(): Unit = {}
}
