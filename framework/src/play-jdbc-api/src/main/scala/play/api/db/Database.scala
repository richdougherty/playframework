/*
 * Copyright (C) 2009-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package play.api.db

import java.sql.Connection
import javax.sql.DataSource

import scala.concurrent.{ ExecutionContext, Future }

/**
 * Database API.
 */
trait Database {

  /**
   * The configuration name for this database.
   */
  def name: String

  /**
   * The underlying JDBC data source for this database.
   */
  def dataSource: DataSource

  /**
   * The JDBC connection URL this database, i.e. `jdbc:...`
   * Normally retrieved via a connection.
   */
  def url: String

  def getConnectionAsync(): Future[Connection]

  /**
   * Get a JDBC connection from the underlying data source.
   * Autocommit is enabled by default.
   *
   * Don't forget to release the connection at some point by calling close().
   *
   * @return a JDBC connection
   */
  def getConnection(): Connection

  def getConnectionAsync(autocommit: Boolean): Future[Connection]

  /**
   * Get a JDBC connection from the underlying data source.
   *
   * Don't forget to release the connection at some point by calling close().
   *
   * @param autocommit determines whether to autocommit the connection
   * @return a JDBC connection
   */
  def getConnection(autocommit: Boolean): Connection

  def withConnectionAsync[A](block: Connection => Future[A])(implicit ec: ExecutionContext): Future[A]

  /**
   * Execute a block of code, providing a JDBC connection.
   * The connection and all created statements are automatically released.
   *
   * @param block code to execute
   * @return the result of the code block
   */
  def withConnection[A](block: Connection => A): A

  def withConnectionAsync[A](autocommit: Boolean)(block: Connection => Future[A])(implicit ec: ExecutionContext): Future[A]

  /**
   * Execute a block of code, providing a JDBC connection.
   * The connection and all created statements are automatically released.
   *
   * @param autocommit determines whether to autocommit the connection
   * @param block code to execute
   * @return the result of the code block
   */
  def withConnection[A](autocommit: Boolean)(block: Connection => A): A

  def withTransactionAsync[A](block: Connection => Future[A])(implicit ec: ExecutionContext): Future[A]
  /**
   * Execute a block of code in the scope of a JDBC transaction.
   * The connection and all created statements are automatically released.
   * The transaction is automatically committed, unless an exception occurs.
   *
   * @param block code to execute
   * @return the result of the code block
   */
  def withTransaction[A](block: Connection => A): A

  /**
   * Shutdown this database, closing the underlying data source.
   */
  def shutdown(): Unit

}
