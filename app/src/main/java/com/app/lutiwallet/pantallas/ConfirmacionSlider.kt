package com.app.lutiwallet.pantallas

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.forEachGesture
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun ConfirmacionSlider(
    onConfirmado: () -> Unit,
    habilitado: Boolean = true,
    texto: String = "Deslizá para confirmar"
) {
    val density = LocalDensity.current
    val thumbSizeDp = 56.dp
    val thumbSizePx = with(density) { thumbSizeDp.toPx() }

    var trackWidthPx by remember { mutableStateOf(0f) }
    var offsetX by remember { mutableStateOf(0f) }
    var confirmado by remember { mutableStateOf(false) }

    val maxOffset = (trackWidthPx - thumbSizePx).coerceAtLeast(0f)
    val progress = if (maxOffset > 0f) (offsetX / maxOffset).coerceIn(0f, 1f) else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = if (confirmado) 1f else progress,
        label = "sliderProgress"
    )

    LaunchedEffect(confirmado) {
        if (confirmado) {
            delay(300)
            onConfirmado()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(thumbSizeDp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White.copy(0.08f))
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
    ) {
        if (trackWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .background(
                        if (confirmado) Color.Green.copy(0.4f) else Color.Cyan.copy(0.25f),
                        RoundedCornerShape(28.dp)
                    )
            )
        }

        Text(
            text = if (confirmado) "✓ Confirmado" else texto,
            color = Color.White.copy(0.5f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center)
        )

        if (trackWidthPx > 0f) {
            val thumbOffsetDp = with(density) {
                (offsetX.coerceIn(0f, maxOffset)).toDp()
            }

            Box(
                modifier = Modifier
                    .padding(start = thumbOffsetDp)
                    .size(thumbSizeDp)
                    .clip(CircleShape)
                    .background(if (habilitado) Color.Cyan else Color.Gray)
                    .pointerInput(habilitado, maxOffset) {
                        if (!habilitado || maxOffset <= 0f) return@pointerInput

                        // Usamos forEachGesture para manejar cada ciclo de toque
                        forEachGesture {
                            awaitPointerEventScope {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var accumulatedDrag = offsetX

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val dragEvent = event.changes.firstOrNull() ?: break

                                    if (dragEvent.pressed) {
                                        val delta = dragEvent.positionChange().x
                                        accumulatedDrag = (accumulatedDrag + delta).coerceIn(0f, maxOffset)
                                        offsetX = accumulatedDrag
                                        // Consumimos el evento para que el slider se mueva fluido
                                        if (delta != 0f) dragEvent.consume()
                                    } else {
                                        // Soltamos el dedo
                                        if (offsetX / maxOffset >= 0.85f) {
                                            confirmado = true
                                            offsetX = maxOffset
                                        } else {
                                            offsetX = 0f
                                        }
                                        break
                                    }
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (confirmado) Icons.Rounded.Check else Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun DialogoConfirmacion(
    titulo: String,
    onDismiss: () -> Unit,
    onConfirmado: () -> Unit,
    ejecutando: Boolean = false,
    contenido: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!ejecutando) onDismiss() },
        containerColor = Color(0xFF1A1A1A),
        title = {
            Text(titulo, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                contenido()
                Spacer(Modifier.height(8.dp))
                if (ejecutando) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color.Cyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Procesando...", color = Color.Gray, fontSize = 13.sp)
                    }
                } else {
                    ConfirmacionSlider(onConfirmado = onConfirmado)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            if (!ejecutando) {
                TextButton(onClick = onDismiss) {
                    Text("CANCELAR", color = Color.Gray)
                }
            }
        }
    )
}