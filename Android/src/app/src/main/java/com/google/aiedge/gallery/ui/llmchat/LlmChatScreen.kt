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

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.aiedge.gallery.ui.ViewModelProvider
import com.google.aiedge.gallery.ui.common.chat.ChatMessageText
import com.google.aiedge.gallery.ui.common.chat.ChatView
import com.google.aiedge.gallery.ui.modelmanager.ModelManagerViewModel
import kotlinx.serialization.Serializable

/** Navigation destination data */
object LlmChatDestination {
  @Serializable
  val route = "LlmChatRoute"
}

@Composable
fun LlmChatScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: LlmChatViewModel = viewModel(
    factory = ViewModelProvider.Factory
  ),
) {
  ChatView(
    task = viewModel.task,
    viewModel = viewModel,
    modelManagerViewModel = modelManagerViewModel,
    onSendMessage = { model, message ->
      viewModel.addMessage(
        model = model,
        message = message,
      )
      if (message is ChatMessageText) {
        modelManagerViewModel.addTextInputHistory(message.content)
        viewModel.generateResponse(
          model = model,
          input = message.content,
        )
      }
    },
    onRunAgainClicked = { model, message ->
      if (message is ChatMessageText) {
        viewModel.runAgain(model = model, message = message)
      }
    },
    onBenchmarkClicked = { model, message, warmUpIterations, benchmarkIterations ->
      if (message is ChatMessageText) {
        viewModel.benchmark(
          model = model,
          message = message
        )
      }
    },
    navigateUp = navigateUp,
    modifier = modifier,
  )
}

