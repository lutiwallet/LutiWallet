package com.app.lutiwallet.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

@Composable
fun ImagenToken(
    symbol: String,
    mintAddress: String,
    logoUrl: String,
    size: Dp = 44.dp
) {
    var imagenFallo by remember(mintAddress) { mutableStateOf(false) }
    var intentoActual by remember(mintAddress) { mutableIntStateOf(0) }

    val urls = remember(mintAddress, logoUrl) {
        buildList {
            if (logoUrl.isNotEmpty()) add(logoUrl)
            add("https://cdn.jsdelivr.net/gh/solana-labs/token-list@main/assets/mainnet/$mintAddress/logo.png")
            add("https://raw.githubusercontent.com/solana-labs/token-list/main/assets/mainnet/$mintAddress/logo.png")
        }.distinct()
    }

    val urlActual = if (intentoActual < urls.size) urls[intentoActual] else null

    if (urlActual != null && !imagenFallo) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(urlActual)
                .crossfade(true)
                .build(),
            contentDescription = symbol,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.White.copy(0.05f), CircleShape),
            onState = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    if (intentoActual < urls.size - 1) {
                        intentoActual++
                    } else {
                        imagenFallo = true
                    }
                }
            }
        )
    } else {

        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(Color.Cyan.copy(0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol.take(2).uppercase(),
                color = Color.Cyan,
                fontWeight = FontWeight.Black,
                fontSize = (size.value * 0.3f).sp
            )
        }
    }
}