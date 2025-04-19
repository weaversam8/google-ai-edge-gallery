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

import android.content.Context
import com.google.aiedge.gallery.ui.common.chat.PromptTemplate
import com.google.aiedge.gallery.ui.common.convertValueToTargetType
import com.google.aiedge.gallery.ui.llmchat.DEFAULT_ACCELERATORS
import com.google.aiedge.gallery.ui.llmchat.createLlmChatConfigs

data class ModelDataFile(
  val name: String,
  val url: String,
  val downloadFileName: String,
  val sizeInBytes: Long,
)

enum class Accelerator(val label: String) {
  CPU(label = "CPU"), GPU(label = "GPU")
}

const val IMPORTS_DIR = "__imports"

/** A model for a task */
data class Model(
  /** The Hugging Face model ID (if applicable). */
  val hfModelId: String = "",

  /** The name (for display purpose) of the model. */
  val name: String,

  /** The name of the downloaded model file. */
  val downloadFileName: String,

  /** The URL to download the model from. */
  val url: String,

  /** The size of the model file in bytes. */
  val sizeInBytes: Long,

  /** A list of additional data files required by the model. */
  val extraDataFiles: List<ModelDataFile> = listOf(),

  /**
   * A description or information about the model.
   *
   * Will be shown at the start of the chat session and in the expanded model item.
   */
  val info: String = "",

  /**
   * The url to jump to when clicking "learn more" in expanded model item.
   */
  val learnMoreUrl: String = "",

  /** A list of configurable parameters for the model. */
  val configs: List<Config> = listOf(),

  /** Whether to show the "run again" button in the UI. */
  val showRunAgainButton: Boolean = true,

  /** Whether to show the "benchmark" button in the UI. */
  val showBenchmarkButton: Boolean = true,

  /** Indicates whether the model is a zip file. */
  val isZip: Boolean = false,

  /** The name of the directory to unzip the model to (if it's a zip file). */
  val unzipDir: String = "",

  /** The accelerators the the model can run with. */
  val accelerators: List<Accelerator> = DEFAULT_ACCELERATORS,

  /** The prompt templates for the model (only for LLM). */
  val llmPromptTemplates: List<PromptTemplate> = listOf(),

  /** Whether the model is imported or not. */
  val imported: Boolean = false,

  // The following fields are managed by the app. Don't need to set manually.
  var taskType: TaskType? = null,
  var instance: Any? = null,
  var initializing: Boolean = false,
  var configValues: Map<String, Any> = mapOf(),
  var totalBytes: Long = 0L,
  var accessToken: String? = null,
) {
  fun preProcess(task: Task) {
    this.taskType = task.type
    val configValues: MutableMap<String, Any> = mutableMapOf()
    for (config in this.configs) {
      configValues[config.key.label] = config.defaultValue
    }
    this.configValues = configValues
    this.totalBytes = this.sizeInBytes + this.extraDataFiles.sumOf { it.sizeInBytes }
  }

  fun getPath(context: Context, fileName: String = downloadFileName): String {
    val baseDir = "${context.getExternalFilesDir(null)}"
    return if (this.isZip && this.unzipDir.isNotEmpty()) {
      "$baseDir/${this.unzipDir}"
    } else {
      "$baseDir/${fileName}"
    }
  }

  fun getIntConfigValue(key: ConfigKey, defaultValue: Int = 0): Int {
    return getTypedConfigValue(
      key = key, valueType = ValueType.INT, defaultValue = defaultValue
    ) as Int
  }

  fun getFloatConfigValue(key: ConfigKey, defaultValue: Float = 0.0f): Float {
    return getTypedConfigValue(
      key = key, valueType = ValueType.FLOAT, defaultValue = defaultValue
    ) as Float
  }

  fun getBooleanConfigValue(key: ConfigKey, defaultValue: Boolean = false): Boolean {
    return getTypedConfigValue(
      key = key, valueType = ValueType.BOOLEAN, defaultValue = defaultValue
    ) as Boolean
  }

  fun getStringConfigValue(key: ConfigKey, defaultValue: String = ""): String {
    return getTypedConfigValue(
      key = key, valueType = ValueType.STRING, defaultValue = defaultValue
    ) as String
  }

  fun getExtraDataFile(name: String): ModelDataFile? {
    return extraDataFiles.find { it.name == name }
  }

  private fun getTypedConfigValue(key: ConfigKey, valueType: ValueType, defaultValue: Any): Any {
    return convertValueToTargetType(
      value = configValues.getOrDefault(key.label, defaultValue), valueType = valueType
    )
  }
}

/** Data for a imported local model. */
data class ImportedModelInfo(
  val fileName: String,
  val fileSize: Long,
  val defaultValues: Map<String, Any>
)

enum class ModelDownloadStatusType {
  NOT_DOWNLOADED, PARTIALLY_DOWNLOADED, IN_PROGRESS, UNZIPPING, SUCCEEDED, FAILED,
}

data class ModelDownloadStatus(
  val status: ModelDownloadStatusType,
  val totalBytes: Long = 0,
  val receivedBytes: Long = 0,
  val errorMessage: String = "",
  val bytesPerSecond: Long = 0,
  val remainingMs: Long = 0,
)

////////////////////////////////////////////////////////////////////////////////////////////////////
// Configs.

enum class ConfigKey(val label: String) {
  MAX_TOKENS("Max tokens"),
  TOPK("TopK"),
  TOPP("TopP"),
  TEMPERATURE("Temperature"),
  DEFAULT_MAX_TOKENS("Default max tokens"),
  DEFAULT_TOPK("Default TopK"),
  DEFAULT_TOPP("Default TopP"),
  DEFAULT_TEMPERATURE("Default temperature"),
  MAX_RESULT_COUNT("Max result count"),
  USE_GPU("Use GPU"),
  ACCELERATOR("Accelerator"),
  COMPATIBLE_ACCELERATORS("Compatible accelerators"),
  WARM_UP_ITERATIONS("Warm up iterations"),
  BENCHMARK_ITERATIONS("Benchmark iterations"),
  ITERATIONS("Iterations"),
  THEME("Theme"),
  NAME("Name"),
  MODEL_TYPE("Model type")
}

val MOBILENET_CONFIGS: List<Config> = listOf(
  NumberSliderConfig(
    key = ConfigKey.MAX_RESULT_COUNT,
    sliderMin = 1f,
    sliderMax = 5f,
    defaultValue = 3f,
    valueType = ValueType.INT
  ), BooleanSwitchConfig(
    key = ConfigKey.USE_GPU,
    defaultValue = false,
  )
)

val IMAGE_GENERATION_CONFIGS: List<Config> = listOf(
  NumberSliderConfig(
    key = ConfigKey.ITERATIONS,
    sliderMin = 5f,
    sliderMax = 50f,
    defaultValue = 10f,
    valueType = ValueType.INT,
    needReinitialization = false,
  )
)

const val TEXT_CLASSIFICATION_INFO =
  "Model is trained on movie reviews dataset. Type a movie review below and see the scores of positive or negative sentiment."

const val TEXT_CLASSIFICATION_LEARN_MORE_URL =
  "https://ai.google.dev/edge/mediapipe/solutions/text/text_classifier"

const val IMAGE_CLASSIFICATION_INFO = ""

const val IMAGE_CLASSIFICATION_LEARN_MORE_URL = "https://ai.google.dev/edge/litert/android"

const val LLM_CHAT_INFO =
  "Some description about this large language model. A community org for developers to discover models that are ready for deployment to edge platforms"

const val IMAGE_GENERATION_INFO =
  "Powered by [MediaPipe Image Generation API](https://ai.google.dev/edge/mediapipe/solutions/vision/image_generator/android)"

////////////////////////////////////////////////////////////////////////////////////////////////////
// Model spec.

val MODEL_LLM_GEMMA_2B_GPU_INT4: Model = Model(
  name = "Gemma 2B (GPU int4)",
  downloadFileName = "gemma-2b-it-gpu-int4.bin",
  url = "https://storage.googleapis.com/tfweb/app_gallery_models/gemma-2b-it-gpu-int4.bin",
  sizeInBytes = 1354301440L,
  configs = createLlmChatConfigs(),
  info = LLM_CHAT_INFO,
  learnMoreUrl = "https://huggingface.co/litert-community",
)

val MODEL_LLM_GEMMA_2_2B_GPU_INT8: Model = Model(
  name = "Gemma 2 2B (GPU int8)",
  downloadFileName = "gemma2-2b-it-gpu-int8.bin",
  url = "https://storage.googleapis.com/tfweb/app_gallery_models/gemma2-2b-it-gpu-int8.bin",
  sizeInBytes = 2627141632L,
  configs = createLlmChatConfigs(),
  info = LLM_CHAT_INFO,
  learnMoreUrl = "https://huggingface.co/litert-community",
)

val MODEL_LLM_GEMMA_3_1B_INT4: Model = Model(
  name = "Gemma 3 1B (int4)",
  downloadFileName = "gemma3-1b-it-int4.task",
  url = "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task?download=true",
  sizeInBytes = 554661243L,
  accelerators = listOf(Accelerator.CPU, Accelerator.GPU),
  configs = createLlmChatConfigs(
    defaultTopK = 64,
    defaultTopP = 0.95f,
    accelerators = listOf(Accelerator.CPU, Accelerator.GPU)
  ),
  info = LLM_CHAT_INFO,
  learnMoreUrl = "https://huggingface.co/litert-community/Gemma3-1B-IT",
  llmPromptTemplates = listOf(
    PromptTemplate(
      title = "Emoji Fun",
      description = "Generate emojis by emotions",
      prompt = "Show me emojis grouped by emotions"
    ),
    PromptTemplate(
      title = "Trip Planner",
      description = "Plan a trip to a destination",
      prompt = "Plan a two-day trip to San Francisco"
    ),
  )
)

val MODEL_LLM_DEEPSEEK: Model = Model(
  name = "Deepseek",
  downloadFileName = "deepseek.task",
  url = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/deepseek_q8_ekv1280.task?download=true",
  sizeInBytes = 1860686856L,
  accelerators = listOf(Accelerator.CPU),
  configs = createLlmChatConfigs(
    defaultTemperature = 0.6f,
    defaultTopK = 40,
    defaultTopP = 0.7f,
    accelerators = listOf(Accelerator.CPU)
  ),
  info = LLM_CHAT_INFO,
  learnMoreUrl = "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
)

val MODEL_TEXT_CLASSIFICATION_MOBILEBERT: Model = Model(
  name = "MobileBert",
  downloadFileName = "bert_classifier.tflite",
  url = "https://storage.googleapis.com/mediapipe-models/text_classifier/bert_classifier/float32/latest/bert_classifier.tflite",
  sizeInBytes = 25707538L,
  info = TEXT_CLASSIFICATION_INFO,
  learnMoreUrl = TEXT_CLASSIFICATION_LEARN_MORE_URL,
)

val MODEL_TEXT_CLASSIFICATION_AVERAGE_WORD_EMBEDDING: Model = Model(
  name = "Average word embedding",
  downloadFileName = "average_word_classifier.tflite",
  url = "https://storage.googleapis.com/mediapipe-models/text_classifier/average_word_classifier/float32/latest/average_word_classifier.tflite",
  sizeInBytes = 775708L,
  info = TEXT_CLASSIFICATION_INFO,
)

val MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1: Model = Model(
  name = "Mobilenet V1",
  downloadFileName = "mobilenet_v1.tflite",
  url = "https://storage.googleapis.com/tfweb/app_gallery_models/mobilenet_v1.tflite",
  sizeInBytes = 16900760L,
  extraDataFiles = listOf(
    ModelDataFile(
      name = "labels",
      url = "https://raw.githubusercontent.com/leferrad/tensorflow-mobilenet/refs/heads/master/imagenet/labels.txt",
      downloadFileName = "mobilenet_labels_v1.txt",
      sizeInBytes = 21685L
    ),
  ),
  configs = MOBILENET_CONFIGS,
  info = IMAGE_CLASSIFICATION_INFO,
  learnMoreUrl = IMAGE_CLASSIFICATION_LEARN_MORE_URL,
)

val MODEL_IMAGE_CLASSIFICATION_MOBILENET_V2: Model = Model(
  name = "Mobilenet V2",
  downloadFileName = "mobilenet_v2.tflite",
  url = "https://storage.googleapis.com/tfweb/app_gallery_models/mobilenet_v2.tflite",
  sizeInBytes = 13978596L,
  extraDataFiles = listOf(
    ModelDataFile(
      name = "labels",
      url = "https://raw.githubusercontent.com/leferrad/tensorflow-mobilenet/refs/heads/master/imagenet/labels.txt",
      downloadFileName = "mobilenet_labels_v2.txt",
      sizeInBytes = 21685L
    ),
  ),
  configs = MOBILENET_CONFIGS,
  info = IMAGE_CLASSIFICATION_INFO,
)

val MODEL_IMAGE_GENERATION_STABLE_DIFFUSION: Model = Model(
  name = "Stable diffusion",
  downloadFileName = "sd15.zip",
  isZip = true,
  unzipDir = "sd15",
  url = "https://storage.googleapis.com/tfweb/app_gallery_models/sd15.zip",
  sizeInBytes = 1906219565L,
  showRunAgainButton = false,
  showBenchmarkButton = false,
  info = IMAGE_GENERATION_INFO,
  configs = IMAGE_GENERATION_CONFIGS,
  learnMoreUrl = "https://huggingface.co/litert-community",
)

val EMPTY_MODEL: Model = Model(
  name = "empty",
  downloadFileName = "empty.tflite",
  url = "",
  sizeInBytes = 0L,
)

////////////////////////////////////////////////////////////////////////////////////////////////////
// Model collections for different tasks.

val MODELS_TEXT_CLASSIFICATION: MutableList<Model> = mutableListOf(
  MODEL_TEXT_CLASSIFICATION_MOBILEBERT,
  MODEL_TEXT_CLASSIFICATION_AVERAGE_WORD_EMBEDDING,
)

val MODELS_IMAGE_CLASSIFICATION: MutableList<Model> = mutableListOf(
  MODEL_IMAGE_CLASSIFICATION_MOBILENET_V1,
  MODEL_IMAGE_CLASSIFICATION_MOBILENET_V2,
)

val MODELS_LLM_CHAT: MutableList<Model> = mutableListOf(
  MODEL_LLM_GEMMA_2B_GPU_INT4,
  MODEL_LLM_GEMMA_2_2B_GPU_INT8,
  MODEL_LLM_GEMMA_3_1B_INT4,
  MODEL_LLM_DEEPSEEK,
)

val MODELS_IMAGE_GENERATION: MutableList<Model> =
  mutableListOf(MODEL_IMAGE_GENERATION_STABLE_DIFFUSION)
