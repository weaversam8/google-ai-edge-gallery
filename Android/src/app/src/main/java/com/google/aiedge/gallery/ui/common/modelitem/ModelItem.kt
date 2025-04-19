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

package com.google.aiedge.gallery.ui.common.modelitem

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.movableContentWithReceiverOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.ModelDownloadStatusType
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.ui.common.DownloadAndTryButton
import com.google.aiedge.gallery.ui.common.TaskIcon
import com.google.aiedge.gallery.ui.common.chat.MarkdownText
import com.google.aiedge.gallery.ui.common.checkNotificationPermissionAndStartDownload
import com.google.aiedge.gallery.ui.common.getTaskBgColor
import com.google.aiedge.gallery.ui.common.getTaskIconColor
import com.google.aiedge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.MODEL_TEST1
import com.google.aiedge.gallery.ui.preview.MODEL_TEST2
import com.google.aiedge.gallery.ui.preview.MODEL_TEST3
import com.google.aiedge.gallery.ui.preview.MODEL_TEST4
import com.google.aiedge.gallery.ui.preview.PreviewModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.TASK_TEST1
import com.google.aiedge.gallery.ui.preview.TASK_TEST2
import com.google.aiedge.gallery.ui.theme.GalleryTheme

private val DEFAULT_VERTICAL_PADDING = 16.dp

/**
 * Composable function to display a model item in the model manager list.
 *
 * This function renders a card representing a model, displaying its task icon, name,
 * download status, and providing action buttons. It supports expanding to show a
 * model description and buttons for learning more (opening a URL) and downloading/trying
 * the model.
 */
@Composable
fun ModelItem(
  model: Model,
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
  onConfigClicked: () -> Unit = {},
  verticalSpacing: Dp = DEFAULT_VERTICAL_PADDING,
  showDeleteButton: Boolean = true,
  showConfigButtonIfExisted: Boolean = false,
  canExpand: Boolean = true,
) {
  val context = LocalContext.current
  val modelManagerUiState by modelManagerViewModel.uiState.collectAsState()
  val downloadStatus by remember {
    derivedStateOf { modelManagerUiState.modelDownloadStatus[model.name] }
  }
  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) {
    modelManagerViewModel.downloadModel(task = task, model = model)
  }

  var isExpanded by remember { mutableStateOf(false) }

  // Animate alpha for model description and button rows when switching between layouts.
  val alphaAnimation by animateFloatAsState(
    targetValue = if (isExpanded) 1f else 0f,
    animationSpec = tween(durationMillis = LAYOUT_ANIMATION_DURATION - 50)
  )

  LookaheadScope {
    // Task icon.
    val taskIcon = remember {
      movableContentOf {
        TaskIcon(
          task = task, modifier = Modifier.animateLayout()
        )
      }
    }

    // Model name and status.
    val modelNameAndStatus = remember {
      movableContentOf {
        ModelNameAndStatus(
          model = model,
          task = task,
          downloadStatus = downloadStatus,
          isExpanded = isExpanded,
          modifier = Modifier.animateLayout()
        )
      }
    }

    val actionButton = remember {
      movableContentOf {
        ModelItemActionButton(
          context = context,
          model = model,
          task = task,
          modelManagerViewModel = modelManagerViewModel,
          downloadStatus = downloadStatus,
          onDownloadClicked = { model ->
            checkNotificationPermissionAndStartDownload(
              context = context,
              launcher = launcher,
              modelManagerViewModel = modelManagerViewModel,
              task = task,
              model = model
            )
          },
          showDeleteButton = showDeleteButton,
          showDownloadButton = false,
        )
      }
    }

    // Expand/collapse icon, or the config icon.
    val expandButton = remember {
      movableContentOf {
        if (showConfigButtonIfExisted) {
          if (downloadStatus?.status === ModelDownloadStatusType.SUCCEEDED) {
            if (model.configs.isNotEmpty()) {
              IconButton(onClick = onConfigClicked) {
                Icon(
                  Icons.Rounded.Settings,
                  contentDescription = "",
                  tint = getTaskIconColor(task)
                )
              }
            }
          }
        } else {
          Icon(
            // For imported model, show ">" directly indicating users can just tap the model item to
            // go into it without needing to expand it first.
            if (model.imported) Icons.Rounded.ChevronRight else if (isExpanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
            contentDescription = "",
            tint = getTaskIconColor(task),
          )
        }
      }
    }

    // Model description shown in expanded layout.
    val modelDescription = remember {
      movableContentOf { m: Modifier ->
        if (model.info.isNotEmpty()) {
          MarkdownText(
            model.info,
            modifier = Modifier
              .heightIn(min = 0.dp, max = if (isExpanded) 1000.dp else 0.dp)
              .animateLayout()
              .then(m)
          )
        }
      }
    }

    // Button rows shown in expanded layout.
    val buttonRows = remember {
      movableContentOf { m: Modifier ->
        Row(
          modifier = Modifier
            .heightIn(min = 0.dp, max = if (isExpanded) 1000.dp else 0.dp)
            .animateLayout()
            .then(m),
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          // The "learn more" button. Click to show related urls in a bottom sheet.
          if (model.learnMoreUrl.isNotEmpty()) {
            OutlinedButton(
              onClick = {
                if (isExpanded) {
                  val intent = Intent(Intent.ACTION_VIEW, Uri.parse(model.learnMoreUrl))
                  context.startActivity(intent)
                }
              },
            ) {
              Text("Learn More", maxLines = 1)
            }
          }

          // Button to start the download and start the chat session with the model.
          val needToDownloadFirst =
            downloadStatus?.status == ModelDownloadStatusType.NOT_DOWNLOADED || downloadStatus?.status == ModelDownloadStatusType.FAILED
          DownloadAndTryButton(
            task = task,
            model = model,
            enabled = isExpanded,
            needToDownloadFirst = needToDownloadFirst,
            modelManagerViewModel = modelManagerViewModel,
            onClicked = { onModelClicked(model) }
          )
        }
      }
    }

    val container = remember {
      movableContentWithReceiverOf<LookaheadScope, @Composable () -> Unit> { content ->
        Box(
          modifier = Modifier.animateLayout(),
          contentAlignment = Alignment.TopEnd,
        ) {
          content()
        }
      }
    }

    var boxModifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(size = 42.dp))
      .background(
        getTaskBgColor(task)
      )
    boxModifier = if (canExpand) {
      boxModifier.clickable(
        onClick = {
          if (!model.imported) {
            isExpanded = !isExpanded
          } else {
            onModelClicked(model)
          }
        },
        interactionSource = remember { MutableInteractionSource() },
        indication = ripple(
          bounded = true,
          radius = 500.dp,
        )
      )
    } else {
      boxModifier
    }
    Box(
      modifier = boxModifier,
      contentAlignment = Alignment.Center
    ) {
      if (isExpanded) {
        container {
          // The main part (icon, model name, status, etc)
          Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = verticalSpacing, horizontal = 18.dp)
          ) {
            Box(contentAlignment = Alignment.Center) {
              taskIcon()
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
              ) {
                actionButton()
                expandButton()
              }
            }
            modelNameAndStatus()
            modelDescription(Modifier.alpha(alphaAnimation))
            buttonRows(Modifier.alpha(alphaAnimation)) // Apply alpha here
          }
        }
      } else {
        container {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The main part (icon, model name, status, etc)
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp)
                .padding(vertical = verticalSpacing)
            ) {
              taskIcon()
              Row(modifier = Modifier.weight(1f)) {
                modelNameAndStatus()
              }
              Row(verticalAlignment = Alignment.CenterVertically) {
                actionButton()
                expandButton()
              }
            }
            Column(
              modifier = Modifier.offset(y = 30.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              modelDescription(Modifier.alpha(alphaAnimation))
              buttonRows(Modifier.alpha(alphaAnimation))
            }
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun PreviewModelItem() {
  GalleryTheme {
    Column(
      verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(16.dp)
    ) {
      ModelItem(
        model = MODEL_TEST1,
        task = TASK_TEST1,
        onModelClicked = { },
        modelManagerViewModel = PreviewModelManagerViewModel(context = LocalContext.current),
      )
      ModelItem(
        model = MODEL_TEST2,
        task = TASK_TEST1,
        onModelClicked = { },
        modelManagerViewModel = PreviewModelManagerViewModel(context = LocalContext.current),
      )
      ModelItem(
        model = MODEL_TEST3,
        task = TASK_TEST2,
        onModelClicked = { },
        modelManagerViewModel = PreviewModelManagerViewModel(context = LocalContext.current),
      )
      ModelItem(
        model = MODEL_TEST4,
        task = TASK_TEST2,
        onModelClicked = { },
        modelManagerViewModel = PreviewModelManagerViewModel(context = LocalContext.current),
      )
    }
  }
}
