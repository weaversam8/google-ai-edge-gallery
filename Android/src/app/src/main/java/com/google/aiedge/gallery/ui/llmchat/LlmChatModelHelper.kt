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

import android.content.Context
import android.util.Log
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.aiedge.gallery.data.ConfigKey
import com.google.aiedge.gallery.data.LlmBackend
import com.google.aiedge.gallery.data.Model

private const val TAG = "AGLlmChatModelHelper"
private const val DEFAULT_MAX_TOKEN = 1024
private const val DEFAULT_TOPK = 40
private const val DEFAULT_TOPP = 0.9f
private const val DEFAULT_TEMPERATURE = 1.0f

typealias ResultListener = (partialResult: String, done: Boolean) -> Unit
typealias CleanUpListener = () -> Unit

data class LlmModelInstance(val engine: LlmInference, val session: LlmInferenceSession)

object LlmChatModelHelper {
  // Indexed by model name.
  private val cleanUpListeners: MutableMap<String, CleanUpListener> = mutableMapOf()
  private val generateResponseListenableFutures: MutableMap<String, ListenableFuture<String>> =
    mutableMapOf()

  fun initialize(
    context: Context, model: Model, onDone: () -> Unit
  ) {
    val maxTokens =
      model.getIntConfigValue(key = ConfigKey.MAX_TOKENS, defaultValue = DEFAULT_MAX_TOKEN)
    val topK = model.getIntConfigValue(key = ConfigKey.TOPK, defaultValue = DEFAULT_TOPK)
    val topP = model.getFloatConfigValue(key = ConfigKey.TOPP, defaultValue = DEFAULT_TOPP)
    val temperature =
      model.getFloatConfigValue(key = ConfigKey.TEMPERATURE, defaultValue = DEFAULT_TEMPERATURE)
    Log.d(TAG, "Initializing...")
    val preferredBackend = when (model.llmBackend) {
      LlmBackend.CPU -> LlmInference.Backend.CPU
      LlmBackend.GPU -> LlmInference.Backend.GPU
    }
    val options =
      LlmInference.LlmInferenceOptions.builder().setModelPath(model.getPath(context = context))
        .setMaxTokens(maxTokens).setPreferredBackend(preferredBackend).build()

    // Create an instance of the LLM Inference task
    try {
      val llmInference = LlmInference.createFromOptions(context, options)

//      val session = LlmInferenceSession.createFromOptions(
//        llmInference,
//        LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(topK).setTopP(topP)
//          .setTemperature(temperature).build()
//      )
      model.instance = llmInference
//      LlmModelInstance(engine = llmInference, session = session)
    } catch (e: Exception) {
      e.printStackTrace()
    }
    onDone()
  }

  fun cleanUp(model: Model) {
    if (model.instance == null) {
      return
    }

    val instance = model.instance as LlmInference
    try {
      instance.close()
//      instance.session.close()
//      instance.engine.close()
    } catch (e: Exception) {
      // ignore
    }
    val onCleanUp = cleanUpListeners.remove(model.name)
    if (onCleanUp != null) {
      onCleanUp()
    }
    model.instance = null
    Log.d(TAG, "Clean up done.")
  }

  fun runInference(
    model: Model,
    input: String,
    resultListener: ResultListener,
    cleanUpListener: CleanUpListener,
  ) {
    val instance = model.instance as LlmInference

    // Set listener.
    if (!cleanUpListeners.containsKey(model.name)) {
      cleanUpListeners[model.name] = cleanUpListener
    }

    // Start async inference.
    val future = instance.generateResponseAsync(input, resultListener)
    generateResponseListenableFutures[model.name] = future

//    val session = instance.session
//     TODO: need to count token and reset session.
//    session.addQueryChunk(input)
//    session.generateResponseAsync(resultListener)
  }

  fun stopInference(model: Model) {
    val instance = model.instance as LlmInference
    if (instance != null) {
      instance.close()
    }
//    val future = generateResponseListenableFutures[model.name]
//    if (future != null) {
//      future.cancel(true)
//      generateResponseListenableFutures.remove(model.name)
//    }
  }
}
