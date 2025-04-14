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

package com.google.aiedge.gallery.ui.common.chat

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.aiedge.gallery.GalleryTopAppBar
import com.google.aiedge.gallery.data.AppBarAction
import com.google.aiedge.gallery.data.AppBarActionType
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.ModelDownloadStatusType
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.ui.common.checkNotificationPermissonAndStartDownload
import com.google.aiedge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.PreviewChatModel
import com.google.aiedge.gallery.ui.preview.PreviewModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.TASK_TEST1
import com.google.aiedge.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

private const val TAG = "AGChatView"

/**
 * A composable that displays a chat interface, allowing users to interact with different models
 * associated with a given task.
 *
 * This composable provides a horizontal pager for switching between models, a model selector
 * for configuring the selected model, and a chat panel for sending and receiving messages. It also
 * manages model initialization, cleanup, and download status, and handles navigation and system
 * back gestures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatView(
  task: Task,
  viewModel: ChatViewModel,
  modelManagerViewModel: ModelManagerViewModel,
  onSendMessage: (Model, ChatMessage) -> Unit,
  onRunAgainClicked: (Model, ChatMessage) -> Unit,
  onBenchmarkClicked: (Model, ChatMessage, Int, Int) -> Unit,
  navigateUp: () -> Unit,
  modifier: Modifier = Modifier,
  onStreamImageMessage: (Model, ChatMessageImage) -> Unit = { _, _ -> },
  onStopButtonClicked: (Model) -> Unit = {},
  chatInputType: ChatInputType = ChatInputType.TEXT,
  showStopButtonInInputWhenInProgress: Boolean = false,
) {
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val selectedModel = modelManagerUiState.selectedModel

  val pagerState = rememberPagerState(initialPage = task.models.indexOf(selectedModel),
    pageCount = { task.models.size })
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) {
    modelManagerViewModel.downloadModel(selectedModel)
  }

  val handleNavigateUp = {
    navigateUp()

    // clean up all models.
    scope.launch(Dispatchers.Default) {
      for (model in task.models) {
        modelManagerViewModel.cleanupModel(model = model)
      }
    }
  }

  // Initialize model when model/download state changes.
  val status = modelManagerUiState.modelDownloadStatus[selectedModel.name]
  LaunchedEffect(status, selectedModel.name) {
    if (status?.status == ModelDownloadStatusType.SUCCEEDED) {
      Log.d(TAG, "Initializing model '${selectedModel.name}' from ChatView launched effect")
      modelManagerViewModel.initializeModel(context, model = selectedModel)
    }
  }

  // Update selected model and clean up previous model when page is settled on a model page.
  LaunchedEffect(pagerState.settledPage) {
    val curSelectedModel = task.models[pagerState.settledPage]
    Log.d(
      TAG,
      "Pager settled on model '${curSelectedModel.name}' from '${selectedModel.name}'. Updating selected model."
    )
    if (curSelectedModel.name != selectedModel.name) {
      modelManagerViewModel.cleanupModel(model = selectedModel)
    }
    modelManagerViewModel.selectModel(curSelectedModel)
  }

  // Handle system's edge swipe.
  BackHandler {
    handleNavigateUp()
  }

  Scaffold(modifier = modifier, topBar = {
    GalleryTopAppBar(
      title = task.type.label,
      leftAction = AppBarAction(actionType = AppBarActionType.NAVIGATE_UP, actionFn = {
        handleNavigateUp()
      }),
      rightAction = AppBarAction(actionType = AppBarActionType.NO_ACTION, actionFn = {}),
    )
  }) { innerPadding ->
    Box {
      // A horizontal scrollable pager to switch between models.
      HorizontalPager(state = pagerState) { pageIndex ->
        val curSelectedModel = task.models[pageIndex]

        // Calculate the alpha of the current page based on how far they are from the center.
        val pageOffset = (
            (pagerState.currentPage - pageIndex) + pagerState
              .currentPageOffsetFraction
            ).absoluteValue
        val curAlpha = 1f - pageOffset.coerceIn(0f, 1f)

        Column(
          modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
        ) {
          // Model selector at the top.
          ModelSelector(
            model = curSelectedModel,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
            onConfigChanged = { old, new ->
              viewModel.addConfigChangedMessage(
                oldConfigValues = old,
                newConfigValues = new,
                model = curSelectedModel
              )
            },
            modifier = Modifier.fillMaxWidth(),
            contentAlpha = curAlpha,
          )

          // Manages the conditional display of UI elements (download model button and downloading
          // animation) based on the corresponding download status.
          //
          // It uses delayed visibility ensuring they are shown only after a short delay if their
          // respective conditions remain true. This prevents UI flickering and provides a smoother
          // user experience.
          val curStatus = modelManagerUiState.modelDownloadStatus[curSelectedModel.name]
          var shouldShowDownloadingAnimation by remember { mutableStateOf(false) }
          var downloadingAnimationConditionMet by remember { mutableStateOf(false) }
          var shouldShowDownloadModelButton by remember { mutableStateOf(false) }
          var downloadModelButtonConditionMet by remember { mutableStateOf(false) }

          downloadingAnimationConditionMet =
            curStatus?.status == ModelDownloadStatusType.IN_PROGRESS ||
                curStatus?.status == ModelDownloadStatusType.PARTIALLY_DOWNLOADED ||
                curStatus?.status == ModelDownloadStatusType.UNZIPPING
          downloadModelButtonConditionMet =
            curStatus?.status == ModelDownloadStatusType.FAILED ||
                curStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED

          LaunchedEffect(downloadingAnimationConditionMet) {
            if (downloadingAnimationConditionMet) {
              delay(100)
              shouldShowDownloadingAnimation = true
            } else {
              shouldShowDownloadingAnimation = false
            }
          }

          LaunchedEffect(downloadModelButtonConditionMet) {
            if (downloadModelButtonConditionMet) {
              delay(700)
              shouldShowDownloadModelButton = true
            } else {
              shouldShowDownloadModelButton = false
            }
          }

          AnimatedVisibility(
            visible = shouldShowDownloadingAnimation,
            enter = scaleIn(initialScale = 0.9f) + fadeIn(),
            exit = scaleOut(targetScale = 0.9f) + fadeOut()
          ) {
            Box(
              modifier = Modifier.fillMaxSize(),
              contentAlignment = Alignment.Center
            ) {
              ModelDownloadingAnimation()
            }
          }

          AnimatedVisibility(
            visible = shouldShowDownloadModelButton,
            enter = fadeIn(),
            exit = fadeOut()
          ) {
            ModelNotDownloaded(modifier = Modifier.weight(1f), onClicked = {
              checkNotificationPermissonAndStartDownload(
                context = context,
                launcher = launcher,
                modelManagerViewModel = modelManagerViewModel,
                model = curSelectedModel
              )
            })
          }

          // The main messages panel.
          if (curStatus?.status == ModelDownloadStatusType.SUCCEEDED) {
            ChatPanel(
              modelManagerViewModel = modelManagerViewModel,
              task = task,
              selectedModel = curSelectedModel,
              viewModel = viewModel,
              onSendMessage = onSendMessage,
              onRunAgainClicked = onRunAgainClicked,
              onBenchmarkClicked = onBenchmarkClicked,
              onStreamImageMessage = onStreamImageMessage,
              onStreamEnd = { averageFps ->
                viewModel.addMessage(
                  model = curSelectedModel,
                  message = ChatMessageInfo(content = "Live camera session ended. Average FPS: $averageFps")
                )
              },
              onStopButtonClicked = {
                onStopButtonClicked(curSelectedModel)
              },
              modifier = Modifier
                .weight(1f)
                .graphicsLayer { alpha = curAlpha },
              chatInputType = chatInputType,
              showStopButtonInInputWhenInProgress = showStopButtonInInputWhenInProgress,
            )
          }
        }
      }
    }
  }
}

@Preview
@Composable
fun ChatScreenPreview() {
  GalleryTheme {
    val context = LocalContext.current
    val task = TASK_TEST1
    ChatView(
      task = task,
      viewModel = PreviewChatModel(context = context),
      modelManagerViewModel = PreviewModelManagerViewModel(context = context),
      onSendMessage = { _, _ -> },
      onRunAgainClicked = { _, _ -> },
      onBenchmarkClicked = { _, _, _, _ -> },
      navigateUp = {},
    )
  }
}
