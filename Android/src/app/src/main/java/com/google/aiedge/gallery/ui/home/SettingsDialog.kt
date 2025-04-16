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

import androidx.compose.runtime.Composable
import com.google.aiedge.gallery.BuildConfig
import com.google.aiedge.gallery.data.Config
import com.google.aiedge.gallery.data.ConfigKey
import com.google.aiedge.gallery.data.SegmentedButtonConfig
import com.google.aiedge.gallery.ui.common.chat.ConfigDialog
import com.google.aiedge.gallery.ui.theme.THEME_AUTO
import com.google.aiedge.gallery.ui.theme.THEME_DARK
import com.google.aiedge.gallery.ui.theme.THEME_LIGHT

private val CONFIGS: List<Config> = listOf(
  SegmentedButtonConfig(
    key = ConfigKey.THEME,
    defaultValue = THEME_AUTO,
    options = listOf(THEME_AUTO, THEME_LIGHT, THEME_DARK)
  )
)

@Composable
fun SettingsDialog(
  curThemeOverride: String,
  onDismissed: () -> Unit,
  onOk: (Map<String, Any>) -> Unit,
) {
  val initialValues = mapOf(
    ConfigKey.THEME.label to curThemeOverride
  )
  ConfigDialog(
    title = "Settings",
    subtitle = "App version: ${BuildConfig.VERSION_NAME}",
    okBtnLabel = "OK",
    configs = CONFIGS,
    initialValues = initialValues,
    onDismissed = onDismissed,
    onOk = { curConfigValues ->
      onOk(curConfigValues)

      // Hide config dialog.
      onDismissed()
    },
  )
}
