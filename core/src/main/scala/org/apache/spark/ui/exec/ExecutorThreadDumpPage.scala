/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.ui.exec

import javax.servlet.http.HttpServletRequest

import scala.xml.{Node, Text}

import org.apache.spark.SparkContext
import org.apache.spark.ui.{SparkUITab, UIUtils, WebUIPage}

private[ui] class ExecutorThreadDumpPage(
    parent: SparkUITab,
    sc: Option[SparkContext]) extends WebUIPage("threadDump") {

  def render(request: HttpServletRequest): Seq[Node] = {
    val executorId = Option(request.getParameter("executorId")).map { executorId =>
      UIUtils.decodeURLParameter(executorId)
    }.getOrElse {
      throw new IllegalArgumentException(s"Missing executorId parameter")
    }
    val time = System.currentTimeMillis()
    val maybeThreadDump = sc.get.getExecutorThreadDump(executorId)

    val content = maybeThreadDump.map { threadDump =>
      val dumpRows = threadDump.map { thread =>
        val threadId = thread.threadId
        val blockedBy = thread.blockedByThreadId match {
          case Some(blockingThreadId) =>
            <div>
              Blocked by <a href={s"#${blockingThreadId}_td_id"}>
              Thread {blockingThreadId} {thread.blockedByLock}</a>
            </div>
          case None => Text("")
        }
        val synchronizers = thread.synchronizers.map(l => s"Lock($l)")
        val monitors = thread.monitors.map(m => s"Monitor($m)")
        val heldLocks = (synchronizers ++ monitors).mkString(", ")

        <tr id={s"thread_${threadId}_tr"} class="accordion-heading"
            onclick={s"toggleThreadStackTrace($threadId, false)"}
            onmouseover={s"onMouseOverAndOut($threadId)"}
            onmouseout={s"onMouseOverAndOut($threadId)"}>
          <td id={s"${threadId}_td_id"}>{threadId}</td>
          <td id={s"${threadId}_td_name"}>{thread.threadName}</td>
          <td id={s"${threadId}_td_state"}>{thread.threadState}</td>
          <td id={s"${threadId}_td_locking"}>{blockedBy}{heldLocks}</td>
          <td id={s"${threadId}_td_stacktrace"} class="d-none">{thread.stackTrace.html}</td>
        </tr>
      }

    <div class="row">
      <div class="col-12">
        <p>Updated at {UIUtils.formatDate(time)}</p>
        {
          // scalastyle:off
          <div style="display: flex; align-items: center;">
            <a class="expandbutton" onClick="expandAllThreadStackTrace(true)">Expand All</a>
            <a class="expandbutton d-none" onClick="collapseAllThreadStackTrace(true)">Collapse All</a>
            <a class="downloadbutton" href={"data:text/plain;charset=utf-8," + threadDump.map(_.toString).mkString} download={"threaddump_" + executorId + ".txt"}>Download</a>
            <div class="form-inline">
              <div class="bs-example" data-example-id="simple-form-inline">
                <div class="form-group">
                  <div class="input-group">
                    <label class="mr-2" for="search">Search:</label>
                    <input type="text" class="form-control" id="search" oninput="onSearchStringChange()"></input>
                  </div>
                </div>
              </div>
            </div>
          </div>
          <p></p>
          // scalastyle:on
        }
        <table class={UIUtils.TABLE_CLASS_STRIPED + " accordion-group" + " sortable"}>
          <thead>
            <th onClick="collapseAllThreadStackTrace(false)">Thread ID</th>
            <th onClick="collapseAllThreadStackTrace(false)">Thread Name</th>
            <th onClick="collapseAllThreadStackTrace(false)">Thread State</th>
            <th onClick="collapseAllThreadStackTrace(false)">
              <span data-toggle="tooltip" data-placement="top"
                    title="Objects whose lock the thread currently holds">
                Thread Locks
              </span>
            </th>
          </thead>
          <tbody>{dumpRows}</tbody>
        </table>
      </div>
    </div>
    }.getOrElse(Text("Error fetching thread dump"))
    UIUtils.headerSparkPage(request, s"Thread dump for executor $executorId", content, parent)
  }
}
