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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.aiedge.gallery.data.IMPORTS_DIR
import com.google.aiedge.gallery.ui.common.ensureValidFileName
import com.google.aiedge.gallery.ui.common.humanReadableSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

private const val TAG = "AGModelImportDialog"

data class ModelImportInfo(val fileName: String, val fileSize: Long, val error: String = "")

@Composable
fun ModelImportDialog(
  uri: Uri, onDone: (ModelImportInfo) -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var fileName by remember { mutableStateOf("") }
  var fileSize by remember { mutableLongStateOf(0L) }
  var error by remember { mutableStateOf("") }
  var progress by remember { mutableFloatStateOf(0f) }

  LaunchedEffect(Unit) {
    error = ""

    // Get basic info.
    val info = getFileSizeAndDisplayNameFromUri(context = context, uri = uri)
    fileSize = info.first
    fileName = ensureValidFileName(info.second)

    // Import.
    importModel(
      context = context,
      coroutineScope = coroutineScope,
      fileName = fileName,
      fileSize = fileSize,
      uri = uri,
      onDone = {
        onDone(ModelImportInfo(fileName = fileName, fileSize = fileSize, error = error))
      },
      onProgress = {
        progress = it
      },
      onError = {
        error = it
      }
    )
  }

  Dialog(
    properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    onDismissRequest = {},
  ) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
      Column(
        modifier = Modifier
          .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Title.
        Text(
          "Importing...",
          style = MaterialTheme.typography.titleLarge,
          modifier = Modifier.padding(bottom = 8.dp)
        )

        // No error.
        if (error.isEmpty()) {
          // Progress bar.
          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              "$fileName (${fileSize.humanReadableSize()})",
              style = MaterialTheme.typography.labelSmall,
            )
            val animatedProgress = remember { Animatable(0f) }
            LinearProgressIndicator(
              progress = { animatedProgress.value },
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            )
            LaunchedEffect(progress) {
              animatedProgress.animateTo(progress, animationSpec = tween(150))
            }
          }
        }
        // Has error.
        else {
          Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              Icons.Rounded.Error,
              contentDescription = "",
              tint = MaterialTheme.colorScheme.error
            )
            Text(
              error,
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
              modifier = Modifier.padding(top = 4.dp)
            )
          }
          Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = {
              onDone(ModelImportInfo(fileName = "", fileSize = 0L, error = error))
            }) {
              Text("Close")
            }
          }
        }
      }
    }
  }
}

private fun importModel(
  context: Context,
  coroutineScope: CoroutineScope,
  fileName: String,
  fileSize: Long,
  uri: Uri,
  onDone: () -> Unit,
  onProgress: (Float) -> Unit,
  onError: (String) -> Unit,
) {
  // TODO: handle error.
  coroutineScope.launch(Dispatchers.IO) {
    // Get the last component of the uri path as the imported file name.
    val decodedUri = URLDecoder.decode(uri.toString(), StandardCharsets.UTF_8.name())
    Log.d(TAG, "importing model from $decodedUri. File name: $fileName. File size: $fileSize")

    // Create <app_external_dir>/imports if not exist.
    val importsDir = File(context.getExternalFilesDir(null), IMPORTS_DIR)
    if (!importsDir.exists()) {
      importsDir.mkdirs()
    }

    // Import by copying the file over.
    val outputFile = File(context.getExternalFilesDir(null), "$IMPORTS_DIR/$fileName")
    val outputStream = FileOutputStream(outputFile)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var bytesRead: Int
    var lastSetProgressTs: Long = 0
    var importedBytes = 0L
    val inputStream = context.contentResolver.openInputStream(uri)
    try {
      if (inputStream != null) {
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
          outputStream.write(buffer, 0, bytesRead)
          importedBytes += bytesRead

          // Report progress every 200 ms.
          val curTs = System.currentTimeMillis()
          if (curTs - lastSetProgressTs > 200) {
            Log.d(TAG, "importing progress: $importedBytes, $fileSize")
            lastSetProgressTs = curTs
            if (fileSize != 0L) {
              onProgress(importedBytes.toFloat() / fileSize.toFloat())
            }
          }
        }
      }
    } catch (e: Exception) {
      e.printStackTrace()
      onError(e.message ?: "Failed to import")
      return@launch
    } finally {
      inputStream?.close()
      outputStream.close()
    }
    Log.d(TAG, "import done")
    onProgress(1f)
    onDone()
  }
}

private fun getFileSizeAndDisplayNameFromUri(context: Context, uri: Uri): Pair<Long, String> {
  val contentResolver = context.contentResolver
  var fileSize = 0L
  var displayName = ""

  try {
    contentResolver.query(
      uri, arrayOf(OpenableColumns.SIZE, OpenableColumns.DISPLAY_NAME), null, null, null
    )?.use { cursor ->
      if (cursor.moveToFirst()) {
        val sizeIndex = cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)
        fileSize = cursor.getLong(sizeIndex)

        val nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
        displayName = cursor.getString(nameIndex)
      }
    }
  } catch (e: Exception) {
    e.printStackTrace()
    return Pair(0L, "")
  }

  return Pair(fileSize, displayName)
}
