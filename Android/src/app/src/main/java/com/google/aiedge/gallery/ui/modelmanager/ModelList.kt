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

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.ui.common.modelitem.ModelItem
import com.google.aiedge.gallery.ui.preview.PreviewModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.TASK_TEST1
import com.google.aiedge.gallery.ui.theme.GalleryTheme
import com.google.aiedge.gallery.ui.theme.customColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGModelList"

/** The list of models in the model manager. */
@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelList(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  contentPadding: PaddingValues,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showAddModelSheet by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  val curFileUri = remember { mutableStateOf<Uri?>(null) }
  val sheetState = rememberModalBottomSheetState()
  val coroutineScope = rememberCoroutineScope()

  // This is just to update "models" list when task.updateTrigger is updated so that the UI can
  // be properly updated.
  val models by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.toList().filter { !it.isLocalModel }
      } else {
        listOf()
      }
    }
  }
  val localModels by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.toList().filter { it.isLocalModel }
      } else {
        listOf()
      }
    }
  }

  val filePickerLauncher: ActivityResultLauncher<Intent> = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      result.data?.data?.let { uri ->
        curFileUri.value = uri
        showImportingDialog = true
      } ?: run {
        Log.d(TAG, "No file selected or URI is null.")
      }
    } else {
      Log.d(TAG, "File picking cancelled.")
    }
  }

  Box(contentAlignment = Alignment.BottomEnd) {
    LazyColumn(
      modifier = modifier.padding(top = 8.dp),
      contentPadding = contentPadding,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Headline.
      item(key = "headline") {
        Text(
          task.description,
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.fillMaxWidth()
        )
      }

      // URLs.
      item(key = "urls") {
        Row(
          horizontalArrangement = Arrangement.Center,
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 16.dp),
        ) {
          Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            if (task.docUrl.isNotEmpty()) {
              ClickableLink(
                url = task.docUrl, linkText = "API Documentation", icon = Icons.Outlined.Description
              )
            }
            if (task.sourceCodeUrl.isNotEmpty()) {
              ClickableLink(
                url = task.sourceCodeUrl, linkText = "Example code", icon = Icons.Outlined.Code
              )
            }
          }
        }
      }

      // List of models within a task.
      items(items = models) { model ->
        Box {
          ModelItem(
            model = model,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
            onModelClicked = onModelClicked,
            modifier = Modifier.padding(horizontal = 12.dp)
          )
        }
      }

      // Title for local models.
      if (localModels.isNotEmpty()) {
        item(key = "localModelsTitle") {
          Text(
            "Local models",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
              .padding(horizontal = 16.dp)
              .padding(top = 24.dp)
          )
        }
      }

      // List of local models within a task.
      items(items = localModels) { model ->
        Box {
          ModelItem(
            model = model,
            task = task,
            modelManagerViewModel = modelManagerViewModel,
            onModelClicked = onModelClicked,
            modifier = Modifier.padding(horizontal = 12.dp)
          )
        }
      }

      item(key = "bottomPadding") {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }

    // Add model button at the bottom right.
    Box(
      modifier = Modifier
        .padding(end = 16.dp)
        .padding(bottom = contentPadding.calculateBottomPadding())
    ) {
      SmallFloatingActionButton(
        onClick = {
          showAddModelSheet = true
        },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
      ) {
        Icon(Icons.Filled.Add, "")
      }
    }
  }

  if (showAddModelSheet) {
    ModalBottomSheet(
      onDismissRequest = { showAddModelSheet = false },
      sheetState = sheetState,
    ) {
      Text(
        "Add custom model",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
      )
      Box(modifier = Modifier.clickable {
        coroutineScope.launch {
          // Give it sometime to show the click effect.
          delay(200)
          showAddModelSheet = false

          // Show file picker.
          val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
              Intent.EXTRA_MIME_TYPES,
              arrayOf("application/x-binary", "application/octet-stream")
            )
            // Single select.
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)
          }
          filePickerLauncher.launch(intent)
        }
      }) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
        ) {
          Icon(Icons.AutoMirrored.Outlined.NoteAdd, contentDescription = "")
          Text("Add local model")
        }
      }
    }
  }

  if (showImportingDialog) {
    curFileUri.value?.let { uri ->
      ModelImportDialog(uri = uri, onDone = { info ->
        showImportingDialog = false

        if (info.error.isEmpty()) {
          // TODO: support other model types.
          modelManagerViewModel.addLocalLlmModel(
            task = task,
            fileName = info.fileName,
            fileSize = info.fileSize
          )
        }
      })
    }
  }
}

@Composable
fun ClickableLink(
  url: String,
  linkText: String,
  icon: ImageVector,
) {
  val uriHandler = LocalUriHandler.current
  val annotatedText = AnnotatedString(
    text = linkText, spanStyles = listOf(
      AnnotatedString.Range(
        item = SpanStyle(
          color = MaterialTheme.customColors.linkColor, textDecoration = TextDecoration.Underline
        ), start = 0, end = linkText.length
      )
    )
  )

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Icon(icon, contentDescription = "", modifier = Modifier.size(16.dp))
    Text(
      text = annotatedText,
      textAlign = TextAlign.Center,
      style = MaterialTheme.typography.bodySmall,
      modifier = Modifier
        .padding(start = 6.dp)
        .clickable {
          uriHandler.openUri(url)
        },
    )
  }
}

@RequiresApi(Build.VERSION_CODES.O)
@Preview(showBackground = true)
@Composable
fun ModelListPreview() {
  val context = LocalContext.current

  GalleryTheme {
    ModelList(
      task = TASK_TEST1,
      modelManagerViewModel = PreviewModelManagerViewModel(context = context),
      onModelClicked = {},
      contentPadding = PaddingValues(all = 16.dp),
    )
  }
}
