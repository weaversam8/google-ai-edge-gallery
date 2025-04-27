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

package com.google.aiedge.gallery.ui.home

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.aiedge.gallery.GalleryTopAppBar
import com.google.aiedge.gallery.R
import com.google.aiedge.gallery.data.AppBarAction
import com.google.aiedge.gallery.data.AppBarActionType
import com.google.aiedge.gallery.data.ConfigKey
import com.google.aiedge.gallery.data.ImportedModelInfo
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.ui.common.TaskIcon
import com.google.aiedge.gallery.ui.common.getTaskBgColor
import com.google.aiedge.gallery.ui.modelmanager.ModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.PreviewModelManagerViewModel
import com.google.aiedge.gallery.ui.theme.GalleryTheme
import com.google.aiedge.gallery.ui.theme.ThemeSettings
import com.google.aiedge.gallery.ui.theme.customColors
import com.google.aiedge.gallery.ui.theme.titleMediumNarrow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "AGHomeScreen"
private const val TASK_COUNT_ANIMATION_DURATION = 250

/** Navigation destination data */
object HomeScreenDestination {
  @StringRes
  val titleRes = R.string.app_name
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
  modelManagerViewModel: ModelManagerViewModel,
  navigateToTaskScreen: (Task) -> Unit,
  modifier: Modifier = Modifier
) {
  val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
  val uiState by modelManagerViewModel.uiState.collectAsState()
  var showSettingsDialog by remember { mutableStateOf(false) }
  var showImportModelSheet by remember { mutableStateOf(false) }
  val sheetState = rememberModalBottomSheetState()
  var showImportDialog by remember { mutableStateOf(false) }
  var showImportingDialog by remember { mutableStateOf(false) }
  val selectedLocalModelFileUri = remember { mutableStateOf<Uri?>(null) }
  val selectedImportedModelInfo = remember { mutableStateOf<ImportedModelInfo?>(null) }
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val tasks = uiState.tasks
  val loadingHfModels = uiState.loadingHfModels

  val filePickerLauncher: ActivityResultLauncher<Intent> = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    if (result.resultCode == android.app.Activity.RESULT_OK) {
      result.data?.data?.let { uri ->
        selectedLocalModelFileUri.value = uri
        showImportDialog = true
      } ?: run {
        Log.d(TAG, "No file selected or URI is null.")
      }
    } else {
      Log.d(TAG, "File picking cancelled.")
    }
  }

  Scaffold(
    modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      GalleryTopAppBar(
        title = stringResource(HomeScreenDestination.titleRes),
        rightAction = AppBarAction(
          actionType = AppBarActionType.APP_SETTING, actionFn = {
            showSettingsDialog = true
          }
        ),
        loadingHfModels = loadingHfModels,
        scrollBehavior = scrollBehavior,
      )
    },
    floatingActionButton = {
      // A floating action button to show "import model" bottom sheet.
      SmallFloatingActionButton(
        onClick = {
          showImportModelSheet = true
        },
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.secondary,
      ) {
        Icon(Icons.Filled.Add, "")
      }
    }
  ) { innerPadding ->
    Box(contentAlignment = Alignment.BottomCenter, modifier = Modifier.fillMaxSize()) {
      TaskList(
        tasks = tasks,
        navigateToTaskScreen = navigateToTaskScreen,
        modifier = Modifier.fillMaxSize(),
        contentPadding = innerPadding,
      )

      SnackbarHost(hostState = snackbarHostState, modifier = Modifier.padding(bottom = 16.dp))
    }
  }

  // Settings dialog.
  if (showSettingsDialog) {
    SettingsDialog(
      curThemeOverride = modelManagerViewModel.readThemeOverride(),
      onDismissed = { showSettingsDialog = false },
      onOk = { curConfigValues ->
        // Update theme settings.
        // This will update app's theme.
        val themeOverride = curConfigValues[ConfigKey.THEME.label] as String
        ThemeSettings.themeOverride.value = themeOverride

        // Save to data store.
        modelManagerViewModel.saveThemeOverride(themeOverride)
      },
    )
  }

  // Import model bottom sheet.
  if (showImportModelSheet) {
    ModalBottomSheet(
      onDismissRequest = { showImportModelSheet = false },
      sheetState = sheetState,
    ) {
      Text(
        "Import model",
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 16.dp)
      )
      Box(modifier = Modifier.clickable {
        coroutineScope.launch {
          // Give it sometime to show the click effect.
          delay(200)
          showImportModelSheet = false

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
          Text("From local model file")
        }
      }
    }
  }

  // Import dialog
  if (showImportDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      ModelImportDialog(uri = uri,
        onDismiss = { showImportDialog = false },
        onDone = { info ->
          selectedImportedModelInfo.value = info
          showImportDialog = false
          showImportingDialog = true
        })
    }
  }

  // Importing in progress dialog.
  if (showImportingDialog) {
    selectedLocalModelFileUri.value?.let { uri ->
      selectedImportedModelInfo.value?.let { info ->
        ModelImportingDialog(
          uri = uri,
          info = info,
          onDismiss = { showImportingDialog = false },
          onDone = {
            modelManagerViewModel.addImportedLlmModel(
              info = it,
            )
            showImportingDialog = false

            // Show a snack bar for successful import.
            scope.launch {
              snackbarHostState.showSnackbar("✅ Model imported successfully")
            }
          })
      }
    }
  }
}

@Composable
private fun TaskList(
  tasks: List<Task>,
  navigateToTaskScreen: (Task) -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  Box(modifier = modifier.fillMaxSize()) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(count = 2),
      contentPadding = contentPadding,
      modifier = modifier.padding(12.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      // Headline.
      item(key = "headline", span = { GridItemSpan(2) }) {
        Text(
          "Welcome to AI Edge Gallery! Explore a world of \namazing on-device models from LiteRT community",
          textAlign = TextAlign.Center,
          style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.padding(bottom = 20.dp)
        )
      }

      // Cards.
      items(tasks) { task ->
        TaskCard(
          task = task,
          onClick = {
            navigateToTaskScreen(task)
          },
          modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
        )
      }

      // Bottom padding.
      item(key = "bottomPadding", span = { GridItemSpan(2) }) {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }

    // Gradient overlay at the bottom.
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(LocalConfiguration.current.screenHeightDp.dp * 0.25f)
        .background(
          Brush.verticalGradient(
            colors = MaterialTheme.customColors.homeBottomGradient,
          )
        )
        .align(Alignment.BottomCenter)
    )
  }
}

@Composable
private fun TaskCard(task: Task, onClick: () -> Unit, modifier: Modifier = Modifier) {
  // Observes the model count and updates the model count label with a fade-in/fade-out animation
  // whenever the count changes.
  val modelCount by remember {
    derivedStateOf {
      val trigger = task.updateTrigger.value
      if (trigger >= 0) {
        task.models.size
      } else {
        0
      }
    }
  }
  val modelCountLabel by remember {
    derivedStateOf {
      when (modelCount) {
        1 -> "1 Model"
        else -> "%d Models".format(modelCount)
      }
    }
  }
  var curModelCountLabel by remember { mutableStateOf("") }
  var modelCountLabelVisible by remember { mutableStateOf(true) }
  val modelCountAlpha: Float by animateFloatAsState(
    targetValue = if (modelCountLabelVisible) 1f else 0f,
    animationSpec = tween(durationMillis = TASK_COUNT_ANIMATION_DURATION)
  )
  val modelCountScale: Float by animateFloatAsState(
    targetValue = if (modelCountLabelVisible) 1f else 0.7f,
    animationSpec = tween(durationMillis = TASK_COUNT_ANIMATION_DURATION)
  )

  LaunchedEffect(modelCountLabel) {
    if (curModelCountLabel.isEmpty()) {
      curModelCountLabel = modelCountLabel
    } else {
      modelCountLabelVisible = false
      delay(TASK_COUNT_ANIMATION_DURATION.toLong())
      curModelCountLabel = modelCountLabel
      modelCountLabelVisible = true
    }
  }

  Card(
    modifier = modifier
      .clip(RoundedCornerShape(43.5.dp))
      .clickable(
        onClick = onClick,
      ),
    colors = CardDefaults.cardColors(
      containerColor = getTaskBgColor(task = task)
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
    ) {
      // Icon.
      TaskIcon(task = task)

      Spacer(modifier = Modifier.weight(1f))

      // Title.
      val pair = task.type.label.splitByFirstSpace()
      Text(
        pair.first,
        color = MaterialTheme.colorScheme.primary,
        style = titleMediumNarrow.copy(
          fontSize = 20.sp,
          fontWeight = FontWeight.Bold,
        ),
      )
      if (pair.second.isNotEmpty()) {
        Text(
          pair.second,
          color = MaterialTheme.colorScheme.primary,
          style = titleMediumNarrow.copy(
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
          ),
          modifier = Modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
              placeable.placeRelative(0, -4.dp.roundToPx())
            }
          }
        )
      }

      // Model count.
      Text(
        curModelCountLabel,
        color = MaterialTheme.colorScheme.secondary,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
          .alpha(modelCountAlpha)
          .scale(modelCountScale),
      )
    }
  }
}

private fun String.splitByFirstSpace(): Pair<String, String> {
  val spaceIndex = this.indexOf(' ')
  if (spaceIndex == -1) {
    return Pair(this, "")
  }
  return Pair(this.substring(0, spaceIndex), this.substring(spaceIndex + 1))
}

@Preview
@Composable
fun HomeScreenPreview(
) {
  GalleryTheme {
    HomeScreen(
      modelManagerViewModel = PreviewModelManagerViewModel(context = LocalContext.current),
      navigateToTaskScreen = {},
    )
  }
}

