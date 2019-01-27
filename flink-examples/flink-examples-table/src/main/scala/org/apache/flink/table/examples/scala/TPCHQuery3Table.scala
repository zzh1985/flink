/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.flink.table.examples.scala

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.table.api.{Table, TableEnvironment}
import org.apache.flink.table.api.scala._
import org.apache.flink.table.api.types.DataTypes
import org.apache.flink.table.api.java.{BatchTableEnvironment => JBatchTableEnv}
import org.apache.flink.table.sinks.csv.CsvTableSink
import org.apache.flink.table.sources.csv.CsvTableSource

/**
  * This program implements a modified version of the TPC-H query 3. The
  * example demonstrates how to assign names to fields by extending the Tuple class.
  * The original query can be found at
  * [http://www.tpc.org/tpch/spec/tpch2.16.0.pdf](http://www.tpc.org/tpch/spec/tpch2.16.0.pdf)
  * (page 29).
  *
  * This program implements the following SQL equivalent:
  *
  * {{{
  * SELECT
  *      l_orderkey,
  *      SUM(l_extendedprice*(1-l_discount)) AS revenue,
  *      o_orderdate,
  *      o_shippriority
  * FROM customer,
  *      orders,
  *      lineitem
  * WHERE
  *      c_mktsegment = '[SEGMENT]'
  *      AND c_custkey = o_custkey
  *      AND l_orderkey = o_orderkey
  *      AND o_orderdate < date '[DATE]'
  *      AND l_shipdate > date '[DATE]'
  * GROUP BY
  *      l_orderkey,
  *      o_orderdate,
  *      o_shippriority
  * ORDER BY
  *      revenue desc,
  *      o_orderdate;
  * }}}
  *
  * Input files are plain text CSV files using the pipe character ('|') as field separator
  * as generated by the TPC-H data generator which is available at
  * [http://www.tpc.org/tpch/](a href="http://www.tpc.org/tpch/).
  *
  * Usage:
  * {{{
  * TPCHQuery3Expression <lineitem-csv path> <customer-csv path> <orders-csv path> <result path>
  * }}}
  *
  * This example shows how to:
  *  - Convert DataSets to Tables
  *  - Use Table API expressions
  *
  */
object TPCHQuery3Table {

  // *************************************************************************
  //     PROGRAM
  // *************************************************************************

  def main(args: Array[String]) {
    if (!parseParameters(args)) {
      return
    }

    // set filter date
    val date = "1995-03-12".toDate

    // get execution environment
    val env = StreamExecutionEnvironment.getExecutionEnvironment
    val tEnv = TableEnvironment.getBatchTableEnvironment(env)

    val lineitems = getLineitemTable(tEnv, env)
      .filter('l_shipdate.toDate > date)

    val customers = getCustomerTable(tEnv, env)
      .filter('c_mktsegment === "AUTOMOBILE")

    val orders = getOrdersTable(tEnv, env)
      .filter('o_orderdate.toDate < date)

    val items =
      orders.join(customers)
        .where('c_custkey === 'o_orderkey)
        .select('o_orderkey, 'o_orderdate, 'o_shippriority)
      .join(lineitems)
        .where('o_orderkey === 'l_orderkey)
        .select(
          'o_orderkey,
          'l_extendedprice * (1.0f.toExpr - 'l_discount) as 'revenue,
          'o_orderdate,
          'o_shippriority)

    items
      .groupBy('o_orderkey, 'o_orderdate, 'o_shippriority)
      .select('o_orderkey, 'revenue.sum as 'revenue, 'o_orderdate, 'o_shippriority)
      .orderBy('revenue.desc, 'o_orderdate.asc)
      .writeToSink(new CsvTableSink(outputPath, "|", "\n", ""))
    // execute program
    tEnv.execute("Scala TPCH Query 3 (Table API Expression) Example")
  }
  
  // *************************************************************************
  //     USER DATA TYPES
  // *************************************************************************
  
  case class Lineitem(id: Long, extdPrice: Double, discount: Double, shipDate: String)
  case class Customer(id: Long, mktSegment: String)
  case class Order(orderId: Long, custId: Long, orderDate: String, shipPrio: Long)

  // *************************************************************************
  //     UTIL METHODS
  // *************************************************************************
  
  private var lineitemPath: String = _
  private var customerPath: String = _
  private var ordersPath: String = _
  private var outputPath: String = _

  private def parseParameters(args: Array[String]): Boolean = {
    if (args.length == 4) {
      lineitemPath = args(0)
      customerPath = args(1)
      ordersPath = args(2)
      outputPath = args(3)
      true
    } else {
      System.err.println("This program expects data from the TPC-H benchmark as input data.\n" +
          " Due to legal restrictions, we can not ship generated data.\n" +
          " You can find the TPC-H data generator at http://www.tpc.org/tpch/.\n" +
          " Usage: TPCHQuery3 <lineitem-csv path> <customer-csv path> " +
                             "<orders-csv path> <result path>")
      false
    }
  }

  private def getLineitemTable(tEnv: JBatchTableEnv, execEnv: StreamExecutionEnvironment): Table = {
    val fields = Array(
      "l_orderkey",
      "l_partkey",
      "l_suppkey",
      "l_linenumber",
      "l_quantity",
      "l_extendedprice",
      "l_discount",
      "l_tax",
      "l_returnflag",
      "l_linestatus",
      "l_shipdate",
      "l_commitdate",
      "l_receiptdate",
      "l_shipinstruct",
      "l_shipmode",
      "l_comment"
    )
    val tableSource = CsvTableSource.builder()
      .path(lineitemPath)
      .fieldDelimiter("|")
      .fields(fields,
        Array(
          DataTypes.LONG,
          DataTypes.LONG,
          DataTypes.LONG,
          DataTypes.INT,
          DataTypes.DOUBLE,
          DataTypes.DOUBLE,
          DataTypes.DOUBLE,
          DataTypes.DOUBLE,
          DataTypes.STRING,
          DataTypes.STRING,
          DataTypes.DATE,
          DataTypes.DATE,
          DataTypes.DATE,
          DataTypes.STRING,
          DataTypes.STRING,
          DataTypes.STRING
        )).build()
    val boundedStream = tableSource.getBoundedStream(execEnv)
    tEnv.fromBoundedStream(boundedStream, fields.mkString(", "))
  }

  private def getCustomerTable(tEnv: JBatchTableEnv, execEnv: StreamExecutionEnvironment): Table = {
    val fields = Array(
      "c_custkey",
      "c_name",
      "c_address",
      "c_nationkey",
      "c_phone",
      "c_acctbal",
      "c_mktsegment",
      "c_comment"
    )
    val tableSource = CsvTableSource.builder()
      .path(lineitemPath)
      .fieldDelimiter("|")
      .fields(fields,
        Array(
          DataTypes.LONG,
          DataTypes.STRING,
          DataTypes.STRING,
          DataTypes.LONG,
          DataTypes.STRING,
          DataTypes.DOUBLE,
          DataTypes.STRING,
          DataTypes.STRING
        )).build()
    val boundedStream = tableSource.getBoundedStream(execEnv)
    tEnv.fromBoundedStream(boundedStream, fields.mkString(", "))
  }

  private def getOrdersTable(tEnv: JBatchTableEnv, execEnv: StreamExecutionEnvironment): Table = {
    val fields = Array(
      "o_orderkey",
      "o_custkey",
      "o_orderstatus",
      "o_totalprice",
      "o_orderdate",
      "o_orderpriority",
      "o_clerk",
      "o_shippriority",
      "o_comment"
    )
    val tableSource = CsvTableSource.builder()
      .path(lineitemPath)
      .fieldDelimiter("|")
      .fields(fields,
        Array(
          DataTypes.LONG,
          DataTypes.LONG,
          DataTypes.STRING,
          DataTypes.DOUBLE,
          DataTypes.DATE,
          DataTypes.STRING,
          DataTypes.STRING,
          DataTypes.INT,
          DataTypes.STRING
        )).build()
    val boundedStream = tableSource.getBoundedStream(execEnv)
    tEnv.fromBoundedStream(boundedStream, fields.mkString(", "))
  }
  
}
