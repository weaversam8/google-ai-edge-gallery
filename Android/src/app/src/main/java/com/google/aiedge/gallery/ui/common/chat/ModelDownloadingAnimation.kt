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

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.google.aiedge.gallery.R
import com.google.aiedge.gallery.ui.common.getTaskIconColor
import com.google.aiedge.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.pow

private val GRID_SIZE = 240.dp
private val GRID_SPACING = 0.dp
private const val PAUSE_DURATION = 200
private const val ANIMATION_DURATION = 500
private const val START_SCALE = 0.9f
private const val END_SCALE = 0.6f


/**
 * Composable function to display a loading animation using a 2x2 grid of images with a synchronized
 * scaling and rotation effect.
 */
@Composable
fun ModelDownloadingAnimation() {
  val scale = remember { Animatable(END_SCALE) }

  LaunchedEffect(Unit) { // Run this once
    while (true) {
      // Phase 1: Scale up
      scale.animateTo(
        targetValue = START_SCALE,
        animationSpec = tween(
          durationMillis = ANIMATION_DURATION,
          easing = multiBounceEasing(bounces = 3, decay = 0.02f)
        )
      )
      delay(PAUSE_DURATION.toLong())

      // Phase 2: Scale down
      scale.animateTo(
        targetValue = END_SCALE,
        animationSpec = tween(
          durationMillis = ANIMATION_DURATION,
          easing = multiBounceEasing(bounces = 3, decay = 0.02f)
        )
      )
      delay(PAUSE_DURATION.toLong())
    }
  }

  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier.offset(y = -GRID_SIZE / 8)
  ) {
    LazyVerticalGrid(
      columns = GridCells.Fixed(2),
      horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
      verticalArrangement = Arrangement.spacedBy(GRID_SPACING),
      modifier = Modifier
        .width(GRID_SIZE)
        .height(GRID_SIZE)
    ) {
      itemsIndexed(
        listOf(
          R.drawable.pantegon,
          R.drawable.double_circle,
          R.drawable.circle,
          R.drawable.four_circle
        )
      ) { index, imageResource ->
        val currentScale =
          if (index == 0 || index == 3) scale.value else START_SCALE + END_SCALE - scale.value

        Box(
          modifier = Modifier
            .width((GRID_SIZE - GRID_SPACING) / 2)
            .height((GRID_SIZE - GRID_SPACING) / 2),
          contentAlignment = when (index) {
            0 -> Alignment.BottomEnd
            1 -> Alignment.BottomStart
            2 -> Alignment.TopEnd
            3 -> Alignment.TopStart
            else -> Alignment.Center
          }
        ) {
          Image(
            painter = painterResource(id = imageResource),
            contentDescription = "",
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(getTaskIconColor(index = index)),
            modifier = Modifier
              .graphicsLayer {
                scaleX = currentScale
                scaleY = currentScale
                rotationZ = currentScale * 120
                alpha = 0.8f
              }
              .size(70.dp)
          )
        }
      }
    }

    Text(
      "Feel free to switch apps or lock your device.\n"
          + "The download will continue in the background.\n"
          + "We'll send a notification when it's done.",
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center
    )
  }
}

// Custom Easing function for a multi-bounce effect
fun multiBounceEasing(bounces: Int, decay: Float): Easing = Easing { x ->
  if (x == 1f) {
    1f
  } else {
    -decay.pow(x) * cos((x * (bounces + 0.9f) * Math.PI / 1.3f)).toFloat() + 1f
  }
}

@Preview(showBackground = true)
@Composable
fun ModelDownloadingAnimationPreview() {
  GalleryTheme {
    Row(modifier = Modifier.padding(16.dp)) {
      ModelDownloadingAnimation()
    }
  }
}