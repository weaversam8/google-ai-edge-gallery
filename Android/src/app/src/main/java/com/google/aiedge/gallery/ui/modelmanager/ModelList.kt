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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.ui.common.modelitem.ModelItem
import com.google.aiedge.gallery.ui.preview.PreviewModelManagerViewModel
import com.google.aiedge.gallery.ui.preview.TASK_TEST1
import com.google.aiedge.gallery.ui.theme.GalleryTheme

/** The list of models in the model manager. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelList(
  task: Task,
  modelManagerViewModel: ModelManagerViewModel,
  contentPadding: PaddingValues,
  onModelClicked: (Model) -> Unit,
  modifier: Modifier = Modifier,
) {
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
        modifier = Modifier
          .padding(bottom = 20.dp)
          .fillMaxWidth()
      )
    }

    // List of models within a task.
    items(items = task.models) { model ->
      Box {
        ModelItem(
          model = model,
          task = task,
          modelManagerViewModel = modelManagerViewModel,
          onModelClicked = onModelClicked,
          modifier = Modifier.padding(start = 12.dp, end = 12.dp)
        )
      }
    }
  }
}

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
