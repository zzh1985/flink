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

package org.apache.flink.table.runtime.join

import org.apache.flink.streaming.api.functions.co.CoProcessFunction
import org.apache.flink.table.dataformat.BaseRow
import org.apache.flink.table.plan.FlinkJoinRelType
import org.apache.flink.table.typeutils.BaseRowTypeInfo

/**
  * The function to execute row(event) time bounded stream inner-join.
  */
final class RowTimeBoundedStreamJoin(
    joinType: FlinkJoinRelType,
    leftLowerBound: Long,
    leftUpperBound: Long,
    allowedLateness: Long,
    leftType: BaseRowTypeInfo,
    rightType: BaseRowTypeInfo,
    genJoinFuncName: String,
    genJoinFuncCode: String,
    leftTimeIdx: Int,
    rightTimeIdx: Int)
  extends TimeBoundedStreamJoin(
    joinType,
    leftLowerBound,
    leftUpperBound,
    allowedLateness,
    leftType,
    rightType,
    genJoinFuncName, genJoinFuncCode) {

  /**
    * Get the maximum interval between receiving a row and emitting it (as part of a joined result).
    * This is the time interval by which watermarks need to be held back.
    *
    * @return the maximum delay for the outputs
    */
  def getMaxOutputDelay: Long = Math.max(leftRelativeSize, rightRelativeSize) + allowedLateness

  override def updateOperatorTime(ctx: CoProcessFunction[BaseRow,
    BaseRow, BaseRow]#Context): Unit = {
    leftOperatorTime = if (ctx.timerService().currentWatermark() > 0) {
      ctx.timerService().currentWatermark()
    } else {
      0L
    }
    // We may set different operator times in the future.
    rightOperatorTime = leftOperatorTime
  }

  override def getTimeForLeftStream(
      ctx: CoProcessFunction[BaseRow, BaseRow, BaseRow]#Context,
      row: BaseRow): Long = {
    row.getLong(leftTimeIdx)
  }

  override def getTimeForRightStream(
      ctx: CoProcessFunction[BaseRow, BaseRow, BaseRow]#Context,
      row: BaseRow): Long = {
    row.getLong(rightTimeIdx)
  }

  override def registerTimer(
      ctx: CoProcessFunction[BaseRow, BaseRow, BaseRow]#Context,
      cleanupTime: Long): Unit = {
    // Maybe we can register timers for different streams in the future.
    ctx.timerService().registerEventTimeTimer(cleanupTime)
  }
}
