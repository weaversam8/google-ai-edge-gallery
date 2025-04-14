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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.aiedge.gallery.data.BooleanSwitchConfig
import com.google.aiedge.gallery.data.Config
import com.google.aiedge.gallery.data.NumberSliderConfig
import com.google.aiedge.gallery.data.SegmentedButtonConfig
import com.google.aiedge.gallery.data.ValueType
import com.google.aiedge.gallery.ui.preview.MODEL_TEST1
import com.google.aiedge.gallery.ui.theme.GalleryTheme
import com.google.aiedge.gallery.ui.theme.labelSmallNarrow
import kotlin.Double.Companion.NaN

private const val TAG = "AGConfigDialog"

/**
 * Displays a configuration dialog allowing users to modify settings through various input controls.
 */
@Composable
fun ConfigDialog(
  title: String,
  configs: List<Config>,
  initialValues: Map<String, Any>,
  onDismissed: () -> Unit,
  onOk: (Map<String, Any>) -> Unit,
  okBtnLabel: String = "OK",
  subtitle: String = "",
  showCancel: Boolean = true,
) {
  val values: SnapshotStateMap<String, Any> = remember {
    mutableStateMapOf<String, Any>().apply {
      putAll(initialValues)
    }
  }

  Dialog(onDismissRequest = onDismissed) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
      Column(
        modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Dialog title and subtitle.
        Column {
          Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 8.dp)
          )
          // Subtitle.
          if (subtitle.isNotEmpty()) {
            Text(
              subtitle,
              style = labelSmallNarrow,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.offset(y = (-6).dp)
            )
          }
        }


        // List of config rows.
        for (config in configs) {
          when (config) {
            // Number slider.
            is NumberSliderConfig -> {
              NumberSliderRow(config = config, values = values)
            }

            // Boolean switch.
            is BooleanSwitchConfig -> {
              BooleanSwitchRow(config = config, values = values)
            }

            is SegmentedButtonConfig -> {
              SegmentedButtonRow(config = config, values = values)
            }

            else -> {}
          }
        }
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
          horizontalArrangement = Arrangement.End,
        ) {
          // Cancel button.
          if (showCancel) {
            TextButton(
              onClick = { onDismissed() },
            ) {
              Text("Cancel")
            }
          }

          // Ok button
          Button(
            onClick = {
              Log.d(TAG, "Values from dialog: $values")
              onOk(values.toMap())
            },
          ) {
            Text(okBtnLabel)
          }
        }
      }
    }
  }
}

/**
 * Composable function to display a number slider with an associated text input field.
 *
 * This function renders a row containing a slider and a text field, both used to modify
 * a numeric value. The slider allows users to visually adjust the value within a specified range,
 * while the text field provides precise numeric input.
 */
@Composable
fun NumberSliderRow(config: NumberSliderConfig, values: SnapshotStateMap<String, Any>) {
  Column(modifier = Modifier.fillMaxWidth()) {
    // Field label.
    Text(config.key.label, style = MaterialTheme.typography.titleSmall)

    // Controls row.
    Row(
      modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically
    ) {
      var isFocused by remember { mutableStateOf(false) }
      val focusRequester = remember { FocusRequester() }

      // Number slider.
      val sliderValue = try {
        values[config.key.label] as Float
      } catch (e: Exception) {
        0f
      }
      Slider(modifier = Modifier
        .height(24.dp)
        .weight(1f),
        value = sliderValue,
        valueRange = config.sliderMin..config.sliderMax,
        onValueChange = { values[config.key.label] = it })

      Spacer(modifier = Modifier.width(8.dp))

      // Text field.
      val textFieldValue = try {
        when (config.valueType) {
          ValueType.FLOAT -> {
            "%.2f".format(values[config.key.label] as Float)
          }

          ValueType.INT -> {
            "${(values[config.key.label] as Float).toInt()}"
          }

          else -> {
            ""
          }
        }
      } catch (e: Exception) {
        ""
      }
      // A smaller text field.
      BasicTextField(
        value = textFieldValue,
        modifier = Modifier
          .width(80.dp)
          .focusRequester(focusRequester)
          .onFocusChanged {
            isFocused = it.isFocused
          },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        onValueChange = {
          if (it.isNotEmpty()) {
            values[config.key.label] = it.toFloatOrNull() ?: NaN
          } else {
            values[config.key.label] = NaN
          }
        },
      ) { innerTextField ->
        Box(
          modifier = Modifier.border(
            width = if (isFocused) 2.dp else 1.dp,
            color = if (isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            shape = RoundedCornerShape(4.dp)
          )
        ) {
          Box(modifier = Modifier.padding(8.dp)) {
            innerTextField()
          }
        }
      }
    }
  }
}

/**
 * Composable function to display a row with a boolean switch.
 *
 * This function renders a row containing a label and a switch, allowing users to toggle
 * a boolean value.
 */
@Composable
fun BooleanSwitchRow(config: BooleanSwitchConfig, values: SnapshotStateMap<String, Any>) {
  val switchValue = try {
    values[config.key.label] as Boolean
  } catch (e: Exception) {
    false
  }
  Column(modifier = Modifier.fillMaxWidth()) {
    Text(config.key.label, style = MaterialTheme.typography.titleSmall)
    Switch(checked = switchValue, onCheckedChange = { values[config.key.label] = it })
  }
}

@Composable
fun SegmentedButtonRow(config: SegmentedButtonConfig, values: SnapshotStateMap<String, Any>) {
  var selectedIndex by remember { mutableIntStateOf(config.options.indexOf(values[config.key.label])) }

  Column(modifier = Modifier.fillMaxWidth()) {
    Text(config.key.label, style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow {
      config.options.forEachIndexed { index, label ->
        SegmentedButton(shape = SegmentedButtonDefaults.itemShape(
          index = index, count = config.options.size
        ), onClick = {
          selectedIndex = index
          values[config.key.label] = label
        }, selected = index == selectedIndex, label = { Text(label) })
      }
    }

  }
}

@Composable
@Preview(showBackground = true)
fun ConfigDialogPreview() {
  GalleryTheme {
    val defaultValues: MutableMap<String, Any> = mutableMapOf()
    for (config in MODEL_TEST1.configs) {
      defaultValues[config.key.label] = config.defaultValue
    }

    Column {
      ConfigDialog(
        title = "Dialog title",
        subtitle = "20250413",
        configs = MODEL_TEST1.configs,
        initialValues = defaultValues,
        onDismissed = {},
        onOk = {},
      )
    }
  }
}
