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

package com.google.aiedge.gallery.data

import com.google.aiedge.gallery.ui.llmchat.createLLmChatConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class HfModelSummary(val modelId: String)

@Serializable
data class HfModelDetails(val id: String, val siblings: List<HfModelFile>)

@Serializable
data class HfModelFile(val rfilename: String)

@Serializable(with = ConfigValueSerializer::class)
sealed class ConfigValue {
  @Serializable
  data class IntValue(val value: Int) : ConfigValue()

  @Serializable
  data class FloatValue(val value: Float) : ConfigValue()

  @Serializable
  data class StringValue(val value: String) : ConfigValue()
}

/**
 * Custom serializer for the ConfigValue class.
 *
 * This object implements the KSerializer interface to provide custom serialization and
 * deserialization logic for the ConfigValue class. It handles different types of ConfigValue
 * (IntValue, FloatValue, StringValue) and supports JSON format.
 */
object ConfigValueSerializer : KSerializer<ConfigValue> {
  override val descriptor: SerialDescriptor = buildClassSerialDescriptor("ConfigValue")

  override fun serialize(encoder: Encoder, value: ConfigValue) {
    when (value) {
      is ConfigValue.IntValue -> encoder.encodeInt(value.value)
      is ConfigValue.FloatValue -> encoder.encodeFloat(value.value)
      is ConfigValue.StringValue -> encoder.encodeString(value.value)
    }
  }

  override fun deserialize(decoder: Decoder): ConfigValue {
    val input = decoder as? JsonDecoder
      ?: throw SerializationException("This serializer only works with Json")
    return when (val element = input.decodeJsonElement()) {
      is JsonPrimitive -> {
        if (element.isString) {
          ConfigValue.StringValue(element.content)
        } else if (element.content.contains('.')) {
          ConfigValue.FloatValue(element.content.toFloat())
        } else {
          ConfigValue.IntValue(element.content.toInt())
        }
      }

      else -> throw SerializationException("Expected JsonPrimitive")
    }
  }
}

@Serializable
data class HfModel(
  var id: String = "",
  val task: String,
  val name: String,
  val url: String = "",
  val file: String = "",
  val sizeInBytes: Long,
  val configs: Map<String, ConfigValue>,
) {
  fun toModel(): Model {
    val parts = if (url.isNotEmpty()) {
      url.split('/')
    } else if (file.isNotEmpty()) {
      listOf(file)
    } else {
      listOf("")
    }
    val fileName = "${id}_${(parts.lastOrNull() ?: "")}".replace(Regex("[^a-zA-Z0-9._-]"), "_")

    // Generate configs based on the given default values.
    val configs: List<Config> = when (task) {
      TASK_LLM_CHAT.type.label -> createLLmChatConfig(defaults = configs)
      // todo: add configs for other types.
      else -> listOf()
    }

    // Construct url.
    var modelUrl = url
    if (modelUrl.isEmpty() && file.isNotEmpty()) {
      modelUrl = "https://huggingface.co/${id}/resolve/main/${file}?download=true"
    }

    // Other parameters.
    val showBenchmarkButton = when (task) {
      TASK_LLM_CHAT.type.label -> false
      else -> true
    }
    val showRunAgainButton = when (task) {
      TASK_LLM_CHAT.type.label -> false
      else -> true
    }

    return Model(
      hfModelId = id,
      name = name,
      url = modelUrl,
      sizeInBytes = sizeInBytes,
      downloadFileName = fileName,
      configs = configs,
      showBenchmarkButton = showBenchmarkButton,
      showRunAgainButton = showRunAgainButton,
    )
  }
}

fun getIntConfigValue(configValue: ConfigValue?, default: Int): Int {
  if (configValue == null) {
    return default
  }
  return when (configValue) {
    is ConfigValue.IntValue -> configValue.value
    is ConfigValue.FloatValue -> configValue.value.toInt()
    is ConfigValue.StringValue -> 0
  }
}

fun getFloatConfigValue(configValue: ConfigValue?, default: Float): Float {
  if (configValue == null) {
    return default
  }
  return when (configValue) {
    is ConfigValue.IntValue -> configValue.value.toFloat()
    is ConfigValue.FloatValue -> configValue.value
    is ConfigValue.StringValue -> 0f
  }
}

fun getStringConfigValue(configValue: ConfigValue?, default: String): String {
  if (configValue == null) {
    return default
  }
  return when (configValue) {
    is ConfigValue.IntValue -> "${configValue.value}"
    is ConfigValue.FloatValue -> "${configValue.value}"
    is ConfigValue.StringValue -> configValue.value
  }
}
