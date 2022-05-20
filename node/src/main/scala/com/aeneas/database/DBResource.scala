package com.aeneas.database

import org.iq80.leveldb.{DB, DBIterator, ReadOptions}

trait DBResource extends AutoCloseable {
  def get[V](key: Key[V]): V
  def iterator: DBIterator
}

object DBResource {
  def apply(db: DB): DBResource = new DBResource {
    private[this] val snapshot = db.getSnapshot
    private[this] val readOptions = new ReadOptions().snapshot(snapshot)

    override def get[V](key: Key[V]): V = key.parse(db.get(key.keyBytes, readOptions))

    override val iterator: DBIterator = db.iterator(readOptions)

    override def close(): Unit = {
      iterator.close()
      snapshot.close()
    }
  }
}
