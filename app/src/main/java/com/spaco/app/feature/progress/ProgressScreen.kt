package com.spaco.app.feature.progress

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spaco.app.core.domain.model.Accuracy
import com.spaco.app.core.domain.model.LearningState
import com.spaco.app.core.domain.model.OverallProgress

object ProgressTags {
    const val EMPTY = "progress-empty"
    const val DUE_COUNT = "progress-due"
    const val ACCURACY = "progress-accuracy"
    const val STREAK = "progress-streak"
    const val RING = "progress-ring"
    const val LIMITED = "progress-accuracy-limited"
}

@Composable
fun ProgressRoute(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProgressScreen(state)
}

@Composable
fun ProgressScreen(state: ProgressUiState) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Progress", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        val overall = state.overall
        if (!state.loaded) {
            Text("Loading…")
            return@Column
        }
        if (state.isEmpty || overall == null) {
            Text(
                text = "No practice data yet. Add items and run a session to see progress.",
                modifier = Modifier.testTag(ProgressTags.EMPTY),
            )
            return@Column
        }

        OverallCard(overall)
    }
}

@Composable
private fun OverallCard(overall: OverallProgress) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    mastered = overall.stateCounts[LearningState.MASTERED] ?: 0,
                    total = overall.totalItems,
                )
                Spacer(Modifier.size(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LabelledCount("Due now", overall.dueCount, ProgressTags.DUE_COUNT)
                    LabelledCount("Streak (days)", overall.streakDays, ProgressTags.STREAK)
                }
            }

            Spacer(Modifier.height(12.dp))
            AccuracyRow(overall.accuracy)

            Spacer(Modifier.height(12.dp))
            LearningState.entries.forEach { state ->
                Text(
                    text = "${state.name.lowercase().replaceFirstChar { it.uppercase() }}: " +
                        (overall.stateCounts[state] ?: 0),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun AccuracyRow(accuracy: Accuracy) {
    Column {
        LabelledCount("Accuracy (last ${accuracy.windowDays} days)", accuracy.percent, ProgressTags.ACCURACY, isPercent = true)
        if (!accuracy.hasHistory) {
            Text(
                text = "Not yet available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (accuracy.isLimited) {
            Text(
                text = "Limited history",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(ProgressTags.LIMITED),
            )
        }
    }
}

@Composable
private fun LabelledCount(
    label: String,
    value: Int?,
    tag: String,
    isPercent: Boolean = false,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        if (value == null) {
            Text("—", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag(tag))
        } else {
            AnimatedCount(value = value, suffix = if (isPercent) "%" else "", tag = tag)
        }
    }
}

/**
 * Count-up numbers. The resting value always equals the true value: the
 * animation only smooths the approach to it.
 */
@Composable
private fun AnimatedCount(value: Int, suffix: String, tag: String) {
    val animated by animateIntAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 600),
        label = "count-up",
    )
    Text(
        text = "$animated$suffix",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.testTag(tag),
    )
}

@Composable
private fun ProgressRing(mastered: Int, total: Int) {
    val fraction = if (total == 0) 0f else mastered.toFloat() / total
    val sweep by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 600),
        label = "ring",
    )
    val track = MaterialTheme.colorScheme.surfaceVariant
    val progressColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = Modifier.size(72.dp).testTag(ProgressTags.RING)) {
        val stroke = Stroke(width = 10.dp.toPx())
        val inset = stroke.width / 2
        val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
        drawArc(
            color = track,
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke,
        )
    }
}
