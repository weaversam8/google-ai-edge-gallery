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

package com.google.aiedge.gallery.ui.llmchat

import com.google.aiedge.gallery.data.Config
import com.google.aiedge.gallery.data.ConfigKey
import com.google.aiedge.gallery.data.ConfigValue
import com.google.aiedge.gallery.data.NumberSliderConfig
import com.google.aiedge.gallery.data.ValueType
import com.google.aiedge.gallery.data.getFloatConfigValue
import com.google.aiedge.gallery.data.getIntConfigValue

private const val DEFAULT_MAX_TOKEN = 1024
private const val DEFAULT_TOPK = 40
private const val DEFAULT_TOPP = 0.9f
private const val DEFAULT_TEMPERATURE = 1.0f

fun createLlmChatConfigs(
  defaultMaxToken: Int = DEFAULT_MAX_TOKEN,
  defaultTopK: Int = DEFAULT_TOPK,
  defaultTopP: Float = DEFAULT_TOPP,
  defaultTemperature: Float = DEFAULT_TEMPERATURE
): List<Config> {
  return listOf(
    NumberSliderConfig(
      key = ConfigKey.MAX_TOKENS,
      sliderMin = 100f,
      sliderMax = 1024f,
      defaultValue = defaultMaxToken.toFloat(),
      valueType = ValueType.INT
    ),
    NumberSliderConfig(
      key = ConfigKey.TOPK,
      sliderMin = 5f,
      sliderMax = 40f,
      defaultValue = defaultTopK.toFloat(),
      valueType = ValueType.INT
    ),
    NumberSliderConfig(
      key = ConfigKey.TOPP,
      sliderMin = 0.0f,
      sliderMax = 1.0f,
      defaultValue = defaultTopP,
      valueType = ValueType.FLOAT
    ),
    NumberSliderConfig(
      key = ConfigKey.TEMPERATURE,
      sliderMin = 0.0f,
      sliderMax = 2.0f,
      defaultValue = defaultTemperature,
      valueType = ValueType.FLOAT
    ),
  )
}

fun createLLmChatConfig(defaults: Map<String, ConfigValue>): List<Config> {
  val defaultMaxToken =
    getIntConfigValue(defaults[ConfigKey.MAX_TOKENS.id], default = DEFAULT_MAX_TOKEN)
  val defaultTopK = getIntConfigValue(defaults[ConfigKey.TOPK.id], default = DEFAULT_TOPK)
  val defaultTopP = getFloatConfigValue(defaults[ConfigKey.TOPP.id], default = DEFAULT_TOPP)
  val defaultTemperature =
    getFloatConfigValue(defaults[ConfigKey.TEMPERATURE.id], default = DEFAULT_TEMPERATURE)

  return createLlmChatConfigs(
    defaultMaxToken = defaultMaxToken,
    defaultTopK = defaultTopK,
    defaultTopP = defaultTopP,
    defaultTemperature = defaultTemperature
  )
}
