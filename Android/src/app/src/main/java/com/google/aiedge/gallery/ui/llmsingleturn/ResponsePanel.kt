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

package com.google.aiedge.gallery.ui.llmsingleturn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.TASK_LLM_SINGLE_TURN
import com.google.aiedge.gallery.ui.common.chat.MarkdownText
import com.google.aiedge.gallery.ui.common.chat.MessageBodyBenchmarkLlm
import com.google.aiedge.gallery.ui.common.chat.MessageBodyLoading
import com.google.aiedge.gallery.ui.theme.GalleryTheme

private val OPTIONS = listOf("Response", "Benchmark")
private val ICONS = listOf(Icons.Outlined.AutoAwesome, Icons.Outlined.Timer)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResponsePanel(
  model: Model,
  viewModel: LlmSingleTurnViewModel,
  modifier: Modifier = Modifier,
) {
  val uiState by viewModel.uiState.collectAsState()
  val inProgress = uiState.inProgress
  val initializing = uiState.initializing
  val selectedPromptTemplateType = uiState.selectedPromptTemplateType
  val response = uiState.responsesByModel[model.name]?.get(selectedPromptTemplateType.label) ?: ""
  val benchmark = uiState.benchmarkByModel[model.name]?.get(selectedPromptTemplateType.label)
  val responseScrollState = rememberScrollState()
  var selectedOptionIndex by remember { mutableIntStateOf(0) }
  val clipboardManager = LocalClipboardManager.current

  // Scroll to bottom when response changes.
  LaunchedEffect(response) {
    if (inProgress) {
      responseScrollState.animateScrollTo(responseScrollState.maxValue)
    }
  }

  // Select the "response" tab when prompt template changes.
  LaunchedEffect(selectedPromptTemplateType) {
    selectedOptionIndex = 0
  }

  if (initializing) {
    Box(
      contentAlignment = Alignment.TopStart,
      modifier = modifier
        .fillMaxSize()
        .padding(horizontal = 16.dp)
    ) {
      MessageBodyLoading()
    }
  } else {
    // Message when response is empty.
    if (response.isEmpty()) {
      Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          "Response will appear here",
          modifier = Modifier.alpha(0.5f),
          style = MaterialTheme.typography.labelMedium,
        )
      }
    }
    // Response markdown.
    else {
      Column(
        modifier = modifier
          .padding(horizontal = 16.dp)
          .padding(bottom = 4.dp)
      ) {
        // Response/benchmark switch.
        Row(modifier = Modifier.fillMaxWidth()) {
          PrimaryTabRow(
            selectedTabIndex = selectedOptionIndex,
            containerColor = Color.Transparent,
          ) {
            OPTIONS.forEachIndexed { index, title ->
              Tab(selected = selectedOptionIndex == index, onClick = {
                selectedOptionIndex = index
              }, text = {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Icon(
                    ICONS[index],
                    contentDescription = "",
                    modifier = Modifier
                      .size(16.dp)
                      .alpha(0.7f)
                  )
                  Text(text = title)
                }
              })
            }
          }
        }
        if (selectedOptionIndex == 0) {
          Box(
            contentAlignment = Alignment.BottomEnd,
            modifier = Modifier.weight(1f)
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(responseScrollState)
            ) {
              MarkdownText(
                text = response,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp)
              )
            }
            // Copy button.
            IconButton(
              onClick = {
                val clipData = AnnotatedString(response)
                clipboardManager.setText(clipData)
              },
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary,
              ),
            ) {
              Icon(
                Icons.Outlined.ContentCopy,
                contentDescription = "",
                modifier = Modifier.size(20.dp),
              )
            }
          }
        } else if (selectedOptionIndex == 1) {
          if (benchmark != null) {
            MessageBodyBenchmarkLlm(message = benchmark, modifier = Modifier.fillMaxWidth())
          }
        }
      }
    }
  }
}

@Preview(showBackground = true)
@Composable
fun ResponsePanelPreview() {
  GalleryTheme {
    ResponsePanel(
      model = TASK_LLM_SINGLE_TURN.models[0],
      viewModel = LlmSingleTurnViewModel(),
      modifier = Modifier.fillMaxSize()
    )
  }
}