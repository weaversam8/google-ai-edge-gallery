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
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.aiedge.gallery.data.AGWorkInfo
import com.google.aiedge.gallery.data.AccessTokenData
import com.google.aiedge.gallery.data.DataStoreRepository
import com.google.aiedge.gallery.data.DownloadRepository
import com.google.aiedge.gallery.data.EMPTY_MODEL
import com.google.aiedge.gallery.data.HfModel
import com.google.aiedge.gallery.data.HfModelDetails
import com.google.aiedge.gallery.data.HfModelSummary
import com.google.aiedge.gallery.data.Model
import com.google.aiedge.gallery.data.ModelDownloadStatus
import com.google.aiedge.gallery.data.ModelDownloadStatusType
import com.google.aiedge.gallery.data.TASKS
import com.google.aiedge.gallery.data.Task
import com.google.aiedge.gallery.data.TaskType
import com.google.aiedge.gallery.data.getModelByName
import com.google.aiedge.gallery.ui.common.AuthConfig
import com.google.aiedge.gallery.ui.imageclassification.ImageClassificationModelHelper
import com.google.aiedge.gallery.ui.imagegeneration.ImageGenerationModelHelper
import com.google.aiedge.gallery.ui.llmchat.LlmChatModelHelper
import com.google.aiedge.gallery.ui.textclassification.TextClassificationModelHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.ResponseTypeValues
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "AGModelManagerViewModel"
private const val HG_COMMUNITY = "jinjingforevercommunity"
private const val TEXT_INPUT_HISTORY_MAX_SIZE = 50

enum class ModelInitializationStatus {
  NOT_INITIALIZED, INITIALIZING, INITIALIZED,
}

enum class TokenStatus {
  NOT_STORED, EXPIRED, NOT_EXPIRED,
}

enum class TokenRequestResultType {
  FAILED, SUCCEEDED, USER_CANCELLED
}

data class TokenStatusAndData(
  val status: TokenStatus,
  val data: AccessTokenData?,
)

data class TokenRequestResult(
  val status: TokenRequestResultType,
  val errorMessage: String? = null
)

data class ModelManagerUiState(
  /**
   * A list of tasks available in the application.
   */
  val tasks: List<Task>,

  /**
   * A map that stores lists of models indexed by task name.
   */
  val modelsByTaskName: Map<String, MutableList<Model>>,

  /**
   * A map that tracks the download status of each model, indexed by model name.
   */
  val modelDownloadStatus: Map<String, ModelDownloadStatus>,

  /**
   * A map that tracks the initialization status of each model, indexed by model name.
   */
  val modelInitializationStatus: Map<String, ModelInitializationStatus>,

  /**
   * Whether Hugging Face models from the given community are currently being loaded.
   */
  val loadingHfModels: Boolean = false,

  /**
   * The currently selected model.
   */
  val selectedModel: Model = EMPTY_MODEL,

  /**
   * The history of text inputs entered by the user.
   */
  val textInputHistory: List<String> = listOf(),
)

/**
 * ViewModel responsible for managing models, their download status, and initialization.
 *
 * This ViewModel handles model-related operations such as downloading, deleting, initializing,
 * and cleaning up models. It also manages the UI state for model management, including the
 * list of tasks, models, download statuses, and initialization statuses.
 */
open class ModelManagerViewModel(
  private val downloadRepository: DownloadRepository,
  private val dataStoreRepository: DataStoreRepository,
  context: Context,
) : ViewModel() {
  private val externalFilesDir = context.getExternalFilesDir(null)
  private val inProgressWorkInfos: List<AGWorkInfo> =
    downloadRepository.getEnqueuedOrRunningWorkInfos()
  protected val _uiState = MutableStateFlow(createUiState())
  val uiState = _uiState.asStateFlow()
  val authService = AuthorizationService(context)
  var curAccessToken: String = ""

  init {
    Log.d(TAG, "In-progress worker infos: $inProgressWorkInfos")

    // Iterate through the inProgressWorkInfos and retrieve the corresponding modes.
    // Those models are the ones that have not finished downloading.
    val models: MutableList<Model> = mutableListOf()
    for (info in inProgressWorkInfos) {
      getModelByName(info.modelName)?.let { model ->
        models.add(model)
      }
    }

    // Cancel all pending downloads for the retrieved models.
    downloadRepository.cancelAll(models) {
      Log.d(TAG, "All pending work is cancelled")

      viewModelScope.launch(Dispatchers.IO) {
        // Load models from hg community.
        loadHfModels()
        Log.d(TAG, "Done loading HF models")

        // Kick off downloads for these models .
        withContext(Dispatchers.Main) {
          for (info in inProgressWorkInfos) {
            val model: Model? = getModelByName(info.modelName)
            if (model != null) {
              Log.d(TAG, "Sending a new download request for '${model.name}'")
              downloadRepository.downloadModel(
                model, onStatusUpdated = this@ModelManagerViewModel::setDownloadStatus
              )
            }
          }
        }
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    authService.dispose()
  }

  fun selectModel(model: Model) {
    _uiState.update { _uiState.value.copy(selectedModel = model) }
  }

  fun downloadModel(model: Model) {
    // Update status.
    setDownloadStatus(
      curModel = model, status = ModelDownloadStatus(status = ModelDownloadStatusType.IN_PROGRESS)
    )

    // Delete the model files first.
    deleteModel(model = model)

    // Start to send download request.
    downloadRepository.downloadModel(
      model, onStatusUpdated = this::setDownloadStatus
    )
  }

  fun cancelDownloadModel(model: Model) {
    downloadRepository.cancelDownloadModel(model)
  }

  fun deleteModel(model: Model) {
    deleteFileFromExternalFilesDir(model.downloadFileName)
    for (file in model.extraDataFiles) {
      deleteFileFromExternalFilesDir(file.downloadFileName)
    }
    if (model.isZip && model.unzipDir.isNotEmpty()) {
      deleteDirFromExternalFilesDir(model.unzipDir)
    }

    // Update model download status to NotDownloaded.
    val curModelDownloadStatus = uiState.value.modelDownloadStatus.toMutableMap()
    curModelDownloadStatus[model.name] =
      ModelDownloadStatus(status = ModelDownloadStatusType.NOT_DOWNLOADED)
    val newUiState = uiState.value.copy(modelDownloadStatus = curModelDownloadStatus)
    _uiState.update { newUiState }
  }

  fun initializeModel(context: Context, model: Model, force: Boolean = false) {
    viewModelScope.launch(Dispatchers.Default) {
      // Skip if initialized already.
      if (!force && uiState.value.modelInitializationStatus[model.name] == ModelInitializationStatus.INITIALIZED) {
        Log.d(TAG, "Model '${model.name}' has been initialized. Skipping.")
        return@launch
      }

      // Skip if initialization is in progress.
      if (model.initializing) {
        Log.d(TAG, "Model '${model.name}' is being initialized. Skipping.")
        return@launch
      }

      // Clean up.
      cleanupModel(model = model)

      // Start initialization.
      Log.d(TAG, "Initializing model '${model.name}'...")
      model.initializing = true

      // Show initializing status after a delay. When the delay expires, check if the model has
      // been initialized or not. If so, skip.
      launch {
        delay(500)
        if (model.instance == null) {
          updateModelInitializationStatus(
            model = model, status = ModelInitializationStatus.INITIALIZING
          )
        }
      }

      val onDone: () -> Unit = {
        if (model.instance != null) {
          Log.d(TAG, "Model '${model.name}' initialized successfully")
          model.initializing = false
          updateModelInitializationStatus(
            model = model,
            status = ModelInitializationStatus.INITIALIZED,
          )
        }
      }
      when (model.taskType) {
        TaskType.TEXT_CLASSIFICATION -> TextClassificationModelHelper.initialize(
          context = context,
          model = model,
          onDone = onDone,
        )

        TaskType.IMAGE_CLASSIFICATION -> ImageClassificationModelHelper.initialize(
          context = context,
          model = model,
          onDone = onDone,
        )

        TaskType.LLM_CHAT -> LlmChatModelHelper.initialize(
          context = context,
          model = model,
          onDone = onDone,
        )

        TaskType.IMAGE_GENERATION -> ImageGenerationModelHelper.initialize(
          context = context, model = model, onDone = onDone
        )

        else -> {}
      }
    }
  }

  fun cleanupModel(model: Model) {
    if (model.instance != null) {
      Log.d(TAG, "Cleaning up model '${model.name}'...")
      when (model.taskType) {
        TaskType.TEXT_CLASSIFICATION -> TextClassificationModelHelper.cleanUp(model = model)
        TaskType.IMAGE_CLASSIFICATION -> ImageClassificationModelHelper.cleanUp(model = model)
        TaskType.LLM_CHAT -> LlmChatModelHelper.cleanUp(model = model)
        TaskType.IMAGE_GENERATION -> ImageGenerationModelHelper.cleanUp(model = model)
        else -> {}
      }
      model.instance = null
      model.initializing = false
      updateModelInitializationStatus(
        model = model, status = ModelInitializationStatus.NOT_INITIALIZED
      )
    }
  }

  fun setDownloadStatus(curModel: Model, status: ModelDownloadStatus) {
    // Update model download progress.
    val curModelDownloadStatus = uiState.value.modelDownloadStatus.toMutableMap()
    curModelDownloadStatus[curModel.name] = status
    val newUiState = uiState.value.copy(modelDownloadStatus = curModelDownloadStatus)

    // Delete downloaded file if status is failed or not_downloaded.
    if (status.status == ModelDownloadStatusType.FAILED || status.status == ModelDownloadStatusType.NOT_DOWNLOADED) {
      deleteFileFromExternalFilesDir(curModel.downloadFileName)
    }

    _uiState.update { newUiState }
  }

  fun addTextInputHistory(text: String) {
    if (uiState.value.textInputHistory.indexOf(text) < 0) {
      val newHistory = uiState.value.textInputHistory.toMutableList()
      newHistory.add(0, text)
      if (newHistory.size > TEXT_INPUT_HISTORY_MAX_SIZE) {
        newHistory.removeAt(newHistory.size - 1)
      }
      _uiState.update { _uiState.value.copy(textInputHistory = newHistory) }
      dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
    }
  }

  fun promoteTextInputHistoryItem(text: String) {
    val index = uiState.value.textInputHistory.indexOf(text)
    if (index >= 0) {
      val newHistory = uiState.value.textInputHistory.toMutableList()
      newHistory.removeAt(index)
      newHistory.add(0, text)
      _uiState.update { _uiState.value.copy(textInputHistory = newHistory) }
      dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
    }
  }

  fun deleteTextInputHistory(text: String) {
    val index = uiState.value.textInputHistory.indexOf(text)
    if (index >= 0) {
      val newHistory = uiState.value.textInputHistory.toMutableList()
      newHistory.removeAt(index)
      _uiState.update { _uiState.value.copy(textInputHistory = newHistory) }
      dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
    }
  }

  fun clearTextInputHistory() {
    _uiState.update { _uiState.value.copy(textInputHistory = mutableListOf()) }
    dataStoreRepository.saveTextInputHistory(_uiState.value.textInputHistory)
  }

  fun readThemeOverride(): String {
    return dataStoreRepository.readThemeOverride()
  }

  fun saveThemeOverride(theme: String) {
    dataStoreRepository.saveThemeOverride(theme = theme)
  }

  fun getModelUrlResponse(model: Model, accessToken: String? = null): Int {
    val url = URL(model.url)
    val connection = url.openConnection() as HttpURLConnection
    if (accessToken != null) {
      connection.setRequestProperty(
        "Authorization",
        "Bearer $accessToken"
      )
    }
    connection.connect()

    // Report the result.
    return connection.responseCode
  }

  fun getTokenStatusAndData(): TokenStatusAndData {
    // Try to load token data from DataStore.
    var tokenStatus = TokenStatus.NOT_STORED
    Log.d(TAG, "Reading token data from data store...")
    val tokenData = dataStoreRepository.readAccessTokenData()

    // Token exists.
    if (tokenData != null) {
      Log.d(TAG, "Token exists and loaded.")

      // Check expiration (with 5-minute buffer).
      val curTs = System.currentTimeMillis()
      val expirationTs = tokenData.expiresAtSeconds - 5 * 60
      Log.d(
        TAG,
        "Checking whether token has expired or not. Current ts: $curTs, expires at: $expirationTs"
      )
      if (curTs >= expirationTs) {
        Log.d(TAG, "Token expired!")
        tokenStatus = TokenStatus.EXPIRED
      } else {
        Log.d(TAG, "Token not expired.")
        tokenStatus = TokenStatus.NOT_EXPIRED
        curAccessToken = tokenData.accessToken
      }
    } else {
      Log.d(TAG, "Token doesn't exists.")
    }

    return TokenStatusAndData(status = tokenStatus, data = tokenData)
  }

  fun getAuthorizationRequest(): AuthorizationRequest {
    return AuthorizationRequest.Builder(
      AuthConfig.authServiceConfig,
      AuthConfig.clientId,
      ResponseTypeValues.CODE,
      Uri.parse(AuthConfig.redirectUri)
    ).setScope("read-repos").build()
  }

  fun handleAuthResult(result: ActivityResult, onTokenRequested: (TokenRequestResult) -> Unit) {
    val dataIntent = result.data
    if (dataIntent == null) {
      onTokenRequested(
        TokenRequestResult(
          status = TokenRequestResultType.FAILED,
          errorMessage = "Empty auth result"
        )
      )
      return
    }

    val response = AuthorizationResponse.fromIntent(dataIntent)
    val exception = AuthorizationException.fromIntent(dataIntent)

    when {
      response?.authorizationCode != null -> {
        // Authorization successful, exchange the code for tokens
        var errorMessage: String? = null
        authService.performTokenRequest(
          response.createTokenExchangeRequest()
        ) { tokenResponse, tokenEx ->
          if (tokenResponse != null) {
            if (tokenResponse.accessToken == null) {
              errorMessage = "Empty access token"
            } else if (tokenResponse.refreshToken == null) {
              errorMessage = "Empty refresh token"
            } else if (tokenResponse.accessTokenExpirationTime == null) {
              errorMessage = "Empty expiration time"
            } else {
              // Token exchange successful. Store the tokens securely
              Log.d(TAG, "Token exchange successful. Storing tokens...")
              dataStoreRepository.saveAccessTokenData(
                accessToken = tokenResponse.accessToken!!,
                refreshToken = tokenResponse.refreshToken!!,
                expiresAt = tokenResponse.accessTokenExpirationTime!!
              )
              curAccessToken = tokenResponse.accessToken!!
              Log.d(TAG, "Token successfully saved.")
            }
          } else if (tokenEx != null) {
            errorMessage = "Token exchange failed: ${tokenEx.message}"
          } else {
            errorMessage = "Token exchange failed"
          }
          if (errorMessage == null) {
            onTokenRequested(TokenRequestResult(status = TokenRequestResultType.SUCCEEDED))
          } else {
            onTokenRequested(
              TokenRequestResult(
                status = TokenRequestResultType.FAILED,
                errorMessage = errorMessage
              )
            )
          }
        }
      }

      exception != null -> {
        onTokenRequested(
          TokenRequestResult(
            status = if (exception.message == "User cancelled flow") TokenRequestResultType.USER_CANCELLED else TokenRequestResultType.FAILED,
            errorMessage = "${exception.message}"
          )
        )
      }

      else -> {
        onTokenRequested(
          TokenRequestResult(
            status = TokenRequestResultType.USER_CANCELLED,
          )
        )
      }
    }
  }

  private fun isModelPartiallyDownloaded(model: Model): Boolean {
    return inProgressWorkInfos.find { it.modelName == model.name } != null
  }

  private fun createUiState(): ModelManagerUiState {
    val modelsByTaskName: Map<String, MutableList<Model>> =
      TASKS.associate { task -> task.type.label to task.models }
    val modelDownloadStatus: MutableMap<String, ModelDownloadStatus> = mutableMapOf()
    val modelInstances: MutableMap<String, ModelInitializationStatus> = mutableMapOf()
    for ((_, models) in modelsByTaskName.entries) {
      for (model in models) {
        modelDownloadStatus[model.name] = getModelDownloadStatus(model = model)
        modelInstances[model.name] = ModelInitializationStatus.NOT_INITIALIZED
      }
    }

    val textInputHistory = dataStoreRepository.readTextInputHistory()
    Log.d(TAG, "text input history: $textInputHistory")

    return ModelManagerUiState(
      tasks = TASKS,
      modelsByTaskName = modelsByTaskName,
      modelDownloadStatus = modelDownloadStatus,
      modelInitializationStatus = modelInstances,
      textInputHistory = textInputHistory,
    )
  }

  /**
   * Retrieves the download status of a model.
   *
   * This function determines the download status of a given model by checking if it's fully
   * downloaded, partially downloaded, or not downloaded at all. It also retrieves the received and
   * total bytes for partially downloaded models.
   */
  private fun getModelDownloadStatus(model: Model): ModelDownloadStatus {
    var status = ModelDownloadStatusType.NOT_DOWNLOADED
    var receivedBytes = 0L
    var totalBytes = 0L
    if (isModelDownloaded(model = model)) {
      if (isModelPartiallyDownloaded(model = model)) {
        status = ModelDownloadStatusType.PARTIALLY_DOWNLOADED
        val file = File(externalFilesDir, model.downloadFileName)
        receivedBytes = file.length()
        totalBytes = model.totalBytes
      } else {
        status = ModelDownloadStatusType.SUCCEEDED
      }
    }
    return ModelDownloadStatus(
      status = status, receivedBytes = receivedBytes, totalBytes = totalBytes
    )
  }

  suspend fun loadHfModels() {
    // Update loading state shown in ui.
    _uiState.update {
      uiState.value.copy(
        loadingHfModels = true,
      )
    }

    val modelDownloadStatus = uiState.value.modelDownloadStatus.toMutableMap()
    val modelInstances = uiState.value.modelInitializationStatus.toMutableMap()
    try {
      // Load model summaries.
      val modelSummaries =
        getJsonResponse<List<HfModelSummary>>(url = "https://huggingface.co/api/models?search=$HG_COMMUNITY")
      Log.d(TAG, "HF model summaries: $modelSummaries")

      // Load individual models in parallel.
      if (modelSummaries != null) {
        coroutineScope {
          val hfModels = modelSummaries.map { summary ->
            async {
              val details =
                getJsonResponse<HfModelDetails>(url = "https://huggingface.co/api/models/${summary.modelId}")
              if (details != null && details.siblings.find { it.rfilename == "app.json" } != null) {
                val hfModel =
                  getJsonResponse<HfModel>(url = "https://huggingface.co/${summary.modelId}/resolve/main/app.json")
                if (hfModel != null) {
                  hfModel.id = details.id
                }
                return@async hfModel
              }
              return@async null
            }
          }

          // Process loaded app.json
          for (hfModel in hfModels.awaitAll()) {
            if (hfModel != null) {
              Log.d(TAG, "HF model: $hfModel")
              val task = TASKS.find { it.type.label == hfModel.task }
              val model = hfModel.toModel()
              if (task != null && task.models.find { it.hfModelId == model.hfModelId } == null) {
                model.preProcess(task = task)
                Log.d(TAG, "AG model: $model")
                task.models.add(model)

                // Add initial status and states.
                modelDownloadStatus[model.name] = getModelDownloadStatus(model = model)
                modelInstances[model.name] = ModelInitializationStatus.NOT_INITIALIZED
              }
            }
          }
        }
      }

      _uiState.update {
        uiState.value.copy(
          loadingHfModels = false,
          modelDownloadStatus = modelDownloadStatus,
          modelInitializationStatus = modelInstances
        )
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }
  }

  private inline fun <reified T> getJsonResponse(url: String): T? {
    try {
      val connection = URL(url).openConnection() as HttpURLConnection
      connection.requestMethod = "GET"
      connection.connect()

      val responseCode = connection.responseCode
      if (responseCode == HttpURLConnection.HTTP_OK) {
        val inputStream = connection.inputStream
        val response = inputStream.bufferedReader().use { it.readText() }

        // Parse JSON using kotlinx.serialization
        val json = Json { ignoreUnknownKeys = true } // Handle potential extra fields
        val jsonObj = json.decodeFromString<T>(response)
        return jsonObj
      } else {
        println("HTTP error: $responseCode")
      }
    } catch (e: Exception) {
      e.printStackTrace()
    }

    return null
  }

  private fun isFileInExternalFilesDir(fileName: String): Boolean {
    if (externalFilesDir != null) {
      val file = File(externalFilesDir, fileName)
      return file.exists()
    } else {
      return false
    }
  }

  private fun deleteFileFromExternalFilesDir(fileName: String) {
    if (isFileInExternalFilesDir(fileName)) {
      val file = File(externalFilesDir, fileName)
      file.delete()
    }
  }

  private fun deleteDirFromExternalFilesDir(dir: String) {
    if (isFileInExternalFilesDir(dir)) {
      val file = File(externalFilesDir, dir)
      file.deleteRecursively()
    }
  }

  private fun updateModelInitializationStatus(model: Model, status: ModelInitializationStatus) {
    val curModelInstance = uiState.value.modelInitializationStatus.toMutableMap()
    curModelInstance[model.name] = status
    val newUiState = uiState.value.copy(modelInitializationStatus = curModelInstance)
    _uiState.update { newUiState }
  }

  private fun isModelDownloaded(model: Model): Boolean {
    val downloadedFileExists =
      model.downloadFileName.isNotEmpty() && isFileInExternalFilesDir(model.downloadFileName)

    val unzippedDirectoryExists =
      model.isZip && model.unzipDir.isNotEmpty() && isFileInExternalFilesDir(model.unzipDir)

    // Will also return true if model is partially downloaded.
    return downloadedFileExists || unzippedDirectoryExists
  }
}
