/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.aiedge.gallery.ui.llmchat

import androidx.lifecycle.viewModelScope
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.TASK_LLM_CHAT
import com.google.aiedge.gallery.ui.common.chat.ChatMessageBenchmarkLlmResult
import com.google.aiedge.gallery.ui.common.chat.ChatMessageLoading
import com.google.aiedge.gallery.ui.common.chat.ChatMessageText
import com.google.aiedge.gallery.ui.common.chat.ChatMessageType
import com.google.aiedge.gallery.ui.common.chat.ChatSide
import com.google.aiedge.gallery.ui.common.chat.ChatViewModel
import com.google.aiedge.gallery.ui.common.chat.Stat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGLlmChatViewModel"
private val STATS = listOf(
  Stat(id = "time_to_first_token", label = "Time to 1st token", unit = "sec"),
  Stat(id = "prefill_speed", label = "Prefill speed", unit = "tokens/s"),
  Stat(id = "decode_speed", label = "Decode speed", unit = "tokens/s"),
  Stat(id = "latency", label = "Latency", unit = "sec")
)

class LlmChatViewModel : ChatViewModel(task = TASK_LLM_CHAT) {
  fun generateResponse(model: Model, input: String) {
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)

      // Loading.
      addMessage(
        model = model,
        message = ChatMessageLoading(),
      )

      // Wait for instance to be initialized.
      while (model.instance == null) {
        delay(100)
      }

      // Run inference.
      val instance = model.instance as LlmModelInstance
      val prefillTokens = instance.session.sizeInTokens(input)

      var firstRun = true
      var timeToFirstToken = 0f
      var firstTokenTs = 0L
      var decodeTokens = 0
      var prefillSpeed = 0f
      var decodeSpeed: Float
      val start = System.currentTimeMillis()
      LlmChatModelHelper.runInference(
        model = model,
        input = input,
        resultListener = { partialResult, done ->
          val curTs = System.currentTimeMillis()

          if (firstRun) {
            firstTokenTs = System.currentTimeMillis()
            timeToFirstToken = (firstTokenTs - start) / 1000f
            prefillSpeed = prefillTokens / timeToFirstToken
            firstRun = false
          } else {
            decodeTokens++
          }

          // Remove the last message if it is a "loading" message.
          // This will only be done once.
          val lastMessage = getLastMessage(model = model)
          if (lastMessage?.type == ChatMessageType.LOADING) {
            removeLastMessage(model = model)

            // Add an empty message that will receive streaming results.
            addMessage(
              model = model,
              message = ChatMessageText(content = "", side = ChatSide.AGENT)
            )
          }

          // Incrementally update the streamed partial results.
          val latencyMs: Long = if (done) System.currentTimeMillis() - start else -1
          updateLastTextMessageContentIncrementally(
            model = model,
            partialContent = partialResult,
            latencyMs = latencyMs.toFloat()
          )

          if (done) {
            setInProgress(false)

            decodeSpeed =
              decodeTokens / ((curTs - firstTokenTs) / 1000f)
            if (decodeSpeed.isNaN()) {
              decodeSpeed = 0f
            }

            if (lastMessage is ChatMessageText) {
              updateLastTextMessageLlmBenchmarkResult(
                model = model, llmBenchmarkResult =
                ChatMessageBenchmarkLlmResult(
                  orderedStats = STATS,
                  statValues = mutableMapOf(
                    "prefill_speed" to prefillSpeed,
                    "decode_speed" to decodeSpeed,
                    "time_to_first_token" to timeToFirstToken,
                    "latency" to (curTs - start).toFloat() / 1000f,
                  ),
                  running = false,
                  latencyMs = -1f,
                )
              )
            }
          }
        }, cleanUpListener = {
          setInProgress(false)
        })
    }
  }

  fun runAgain(model: Model, message: ChatMessageText) {
    viewModelScope.launch(Dispatchers.Default) {
      // Wait for model to be initialized.
      while (model.instance == null) {
        delay(100)
      }

      // Clone the clicked message and add it.
      addMessage(model = model, message = message.clone())

      // Run inference.
      generateResponse(
        model = model,
        input = message.content,
      )
    }
  }

  fun benchmark(model: Model, message: ChatMessageText) {
    viewModelScope.launch(Dispatchers.Default) {
      setInProgress(true)

      // Wait for model to be initialized.
      while (model.instance == null) {
        delay(100)
      }
      val instance = model.instance as LlmModelInstance
      val prefillTokens = instance.session.sizeInTokens(message.content)

      // Add the message to show benchmark results.
      val benchmarkLlmResult = ChatMessageBenchmarkLlmResult(
        orderedStats = STATS,
        statValues = mutableMapOf(),
        running = true,
        latencyMs = -1f,
      )
      addMessage(model = model, message = benchmarkLlmResult)

      // Run inference.
      val result = StringBuilder()
      var firstRun = true
      var timeToFirstToken = 0f
      var firstTokenTs = 0L
      var decodeTokens = 0
      var prefillSpeed = 0f
      var decodeSpeed: Float
      val start = System.currentTimeMillis()
      var lastUpdateTime = 0L
      LlmChatModelHelper.runInference(
        model = model,
        input = message.content,
        resultListener = { partialResult, done ->
          val curTs = System.currentTimeMillis()

          if (firstRun) {
            firstTokenTs = System.currentTimeMillis()
            timeToFirstToken = (firstTokenTs - start) / 1000f
            prefillSpeed = prefillTokens / timeToFirstToken
            firstRun = false

            // Update message to show prefill speed.
            replaceLastMessage(
              model = model,
              message = ChatMessageBenchmarkLlmResult(
                orderedStats = STATS,
                statValues = mutableMapOf(
                  "prefill_speed" to prefillSpeed,
                  "time_to_first_token" to timeToFirstToken,
                  "latency" to (curTs - start).toFloat() / 1000f,
                ),
                running = false,
                latencyMs = -1f,
              ),
              type = ChatMessageType.BENCHMARK_LLM_RESULT,
            )
          } else {
            decodeTokens++
          }
          result.append(partialResult)

          if (curTs - lastUpdateTime > 500 || done) {
            decodeSpeed =
              decodeTokens / ((curTs - firstTokenTs) / 1000f)
            if (decodeSpeed.isNaN()) {
              decodeSpeed = 0f
            }
            replaceLastMessage(
              model = model,
              message = ChatMessageBenchmarkLlmResult(
                orderedStats = STATS,
                statValues = mutableMapOf(
                  "prefill_speed" to prefillSpeed,
                  "decode_speed" to decodeSpeed,
                  "time_to_first_token" to timeToFirstToken,
                  "latency" to (curTs - start).toFloat() / 1000f,
                ),
                running = !done,
                latencyMs = -1f,
              ),
              type = ChatMessageType.BENCHMARK_LLM_RESULT
            )
            lastUpdateTime = curTs

            if (done) {
              setInProgress(false)
            }
          }
        },
        cleanUpListener = {
          setInProgress(false)
        }
      )
    }
  }
}

