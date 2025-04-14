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

package com.google.aiedge.gallery.ui.modelmanager

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.google.aiedge.gallery.GalleryTopAppBar
import com.google.aiedge.gallery.data.AppBarAction
import com.google.aiedge.gallery.data.AppBarActionType
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.ModelDownloadStatusType
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.data.getModelByName
import com.google.aiedge.gallery.ui.preview.PreviewModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.TASK_TEST1
import com.google.aiedge.gallery.ui.theme.GalleryTheme

/** A screen to manage models. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelManager(
  task: Task,
  viewModel: ModelManagerViewModel,
  navigateUp: () -> Unit,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val coroutineScope = rememberCoroutineScope()

  // Set title based on the task.
  var title = "${task.type.label} model"
  if (task.models.size != 1) {
    title += "s"
  }

  // Handle system's edge swipe.
  BackHandler {
    navigateUp()
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      GalleryTopAppBar(
        title = title,
//        subtitle = String.format(
//          stringResource(R.string.downloaded_size),
//          totalSizeInBytes.humanReadableSize()
//        ),

        // Refresh model list button at the left side of the app bar.
//        leftAction = AppBarAction(actionType = if (uiState.loadingHfModels) {
//          AppBarActionType.REFRESHING_MODELS
//        } else {
//          AppBarActionType.REFRESH_MODELS
//        }, actionFn = {
//          coroutineScope.launch(Dispatchers.IO) {
//            viewModel.loadHfModels()
//          }
//        }),
        leftAction = AppBarAction(actionType = AppBarActionType.NAVIGATE_UP, actionFn = navigateUp)

        // "Done" button at the right side of the app bar to navigate up.
//        rightAction = AppBarAction(
//          actionType = AppBarActionType.NAVIGATE_UP, actionFn = navigateUp
//        ),
      )
    },
  ) { innerPadding ->
    ModelList(
      task = task,
      modelManagerViewModel = viewModel,
      contentPadding = innerPadding,
      onModelClicked = onModelClicked,
      modifier = Modifier.fillMaxSize()
    )
  }
}

private fun getTotalDownloadedFileSize(uiState: ModelManagerUiState): Long {
  var totalSizeInBytes = 0L
  for ((name, status) in uiState.modelDownloadStatus.entries) {
    if (status.status == ModelDownloadStatusType.SUCCEEDED) {
      totalSizeInBytes += getModelByName(name)?.totalBytes ?: 0L
    } else if (status.status == ModelDownloadStatusType.IN_PROGRESS) {
      totalSizeInBytes += status.receivedBytes
    }
  }
  return totalSizeInBytes
}


@Preview
@Composable
fun ModelManagerPreview() {
  val context = LocalContext.current

  GalleryTheme {
    ModelManager(
      viewModel = PreviewModelManagerViewModel(context = context),
      onModelClicked = {},
      task = TASK_TEST1,
      navigateUp = {},
    )
  }
}
