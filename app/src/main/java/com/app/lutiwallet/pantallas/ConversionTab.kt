package com.app.lutiwallet.pantallas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

private val TASAS_FALLBACK = mapOf(
    "USDT" to 1.0, "USD" to 1.0, "ARS" to 1480.0,
    "BRL" to 5.15, "PYG" to 7400.0, "EUR" to 0.92,
    "CNY" to 7.23, "INR" to 83.3, "AED" to 3.67,
    "GBP" to 0.79, "MXN" to 17.1
)

private val MONEDAS = listOf("USDT", "USD", "ARS", "BRL", "PYG", "EUR", "CNY", "INR", "AED", "GBP", "MXN")

@Composable
fun ConversionTab(
    saldoSol: Double,
    precioSolUsd: Double,
    textos: Map<String, String> = emptyMap()
) {
    var inputAmount by remember { mutableStateOf("") }
    val amount = inputAmount.toDoubleOrNull() ?: 0.0

    var tasas by remember { mutableStateOf(TASAS_FALLBACK) }
    var cargandoTasas by remember { mutableStateOf(true) }
    var ultimaActualizacion by remember { mutableStateOf("") }

    var modoInverso by remember { mutableStateOf(false) }
    var monedaSeleccionada by remember { mutableStateOf("USD") }
    var expandirSelector by remember { mutableStateOf(false) }

    val lblSolAMoneda = textos["sol_a_moneda"] ?: "SOL → Moneda"
    val lblMonedaASol = textos["moneda_a_sol"] ?: "Moneda → SOL"

    LaunchedEffect(Unit) {
        cargandoTasas = true
        try {
            val resultado = withContext(Dispatchers.IO) {
                val client = OkHttpClient()
                val request = Request.Builder()
                    .url("https://open.er-api.com/v6/latest/USD")
                    .build()
                val body = client.newCall(request).execute().body?.string() ?: return@withContext null
                val json = JSONObject(body)
                if (json.optString("result") != "success") return@withContext null
                val rates = json.getJSONObject("rates")
                val nuevasTasas = mutableMapOf<String, Double>()
                for (moneda in MONEDAS) {
                    val key = if (moneda == "USDT") "USD" else moneda
                    nuevasTasas[moneda] = rates.optDouble(key, TASAS_FALLBACK[moneda] ?: 1.0)
                }
                Pair(nuevasTasas, json.optString("time_last_update_utc", "").take(16))
            }
            if (resultado != null) {
                tasas = resultado.first
                ultimaActualizacion = resultado.second
            }
        } catch (_: Exception) {}
        cargandoTasas = false
    }

    Column(Modifier.fillMaxSize().padding(20.dp)) {

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CONVERSOR", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color.White)
            if (cargandoTasas) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.Cyan, strokeWidth = 2.dp)
            } else if (ultimaActualizacion.isNotEmpty()) {
                Text(ultimaActualizacion, color = Color.Gray, fontSize = 10.sp)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Toggle de dirección
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            listOf(false to lblSolAMoneda, true to lblMonedaASol).forEach { (modo, label) ->
                val seleccionado = modoInverso == modo
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (seleccionado) Color.Cyan else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (seleccionado) Color.Transparent else Color.Cyan.copy(0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .clickable { modoInverso = modo; inputAmount = "" },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (seleccionado) Color.Black else Color.Cyan,
                        fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (!modoInverso) {
            // ── SOL → Moneda ─────────────────────────────────────────
            OutlinedTextField(
                value = inputAmount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) inputAmount = it },
                label = { Text("Cantidad en SOL", color = Color.Cyan) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.DarkGray,
                    focusedBorderColor = Color.Cyan
                )
            )
            Spacer(Modifier.height(16.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(MONEDAS) { moneda ->
                    val factor = tasas[moneda] ?: 1.0
                    ConversionRow(moneda, amount * precioSolUsd * factor)
                }
            }

        } else {
            // ── Moneda → SOL ─────────────────────────────────────────
            Box {
                Surface(
                    onClick = { expandirSelector = true },
                    color = Color.White.copy(0.05f),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(monedaSeleccionada, color = Color.White, fontWeight = FontWeight.Bold)
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.Cyan)
                    }
                }
                DropdownMenu(
                    expanded = expandirSelector,
                    onDismissRequest = { expandirSelector = false }
                ) {
                    MONEDAS.forEach { moneda ->
                        DropdownMenuItem(
                            text = { Text(moneda) },
                            onClick = { monedaSeleccionada = moneda; expandirSelector = false; inputAmount = "" }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = inputAmount,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) inputAmount = it },
                label = { Text("Cantidad en $monedaSeleccionada", color = Color.Cyan) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.DarkGray,
                    focusedBorderColor = Color.Cyan
                )
            )

            Spacer(Modifier.height(20.dp))

            if (amount > 0 && precioSolUsd > 0) {
                val factor = tasas[monedaSeleccionada] ?: 1.0
                val solResultado = amount / factor / precioSolUsd
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Cyan.copy(0.08f))
                        .border(1.dp, Color.Cyan.copy(0.2f), RoundedCornerShape(20.dp))
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.SwapVert, null, tint = Color.Cyan, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        String.format(java.util.Locale.US, "%.6f SOL", solResultado),
                        color = Color.White,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        String.format(java.util.Locale.US, "≈ %.4f USD", solResultado * precioSolUsd),
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversionRow(moneda: String, totalFinal: Double) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(0.05f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically
    ) {
        Text(moneda, color = Color.Gray, fontWeight = FontWeight.Medium)
        val formato = if (moneda == "USDT" || moneda == "USD") "%.4f" else "%.2f"
        Text(
            String.format(java.util.Locale.US, formato, totalFinal),
            color = Color.White, fontWeight = FontWeight.Bold
        )
    }
}
