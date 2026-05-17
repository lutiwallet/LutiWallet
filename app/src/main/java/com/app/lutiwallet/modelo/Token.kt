package com.app.lutiwallet.modelo

data class Token(
    val symbol: String,
    val name: String,
    val mintAddress: String,
    val balance: Double,
    val decimals: Int,
    val precioUsd: Double = 0.0,
    val logoUrl: String = "",
    val esFavorito: Boolean = false,
    val cambio24h: Double = 0.0   // % cambio en las últimas 24 h (positivo = sube, negativo = baja)
) {
    val valorUsd: Double get() = balance * precioUsd
    val esSOL: Boolean get() = mintAddress == "So11111111111111111111111111111111111111112"
    val esVerificado: Boolean get() = mintAddress in TOKENS_VERIFICADOS
}


val TOKENS_VERIFICADOS = setOf(
    "So11111111111111111111111111111111111111112",  // SOL
    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", // USDC
    "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", // USDT
    "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", // BONK
    "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R", // RAY
    "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",  // JUP
    "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm", // WIF
    "jtojtomepa8bdya6NkBeinmnwyclTDRevqkPoj17iKz",  // JTO
    "HZ1JovNiVvGrCNiiYWY1ZZcnCxcD4UETQ5FJ3EzKP8S",  // PYTH
    "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE",  // ORCA
    "MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac",  // MNGO
    "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", // SAMO
    "StepAscQoEioFxxWGnh2sLBDFp9d8rvKz2Yp39iDpyT",  // STEP
    "EchesyfXePKdLtoiZSL8ppeVEq2oRFpFnoD5hYKBmT9N", // FIDA
    "DUSTawucrTsGU8hcqRdHDCbuYhCPADMLM2VcCb8VnFnQ"  // DUST
)

private const val CDN = "https://cdn.jsdelivr.net/gh/solana-labs/token-list@main/assets/mainnet"

val TOKENS_POPULARES = listOf(
    Token("SOL",  "Solana",       "So11111111111111111111111111111111111111112",  0.0, 9, logoUrl = "$CDN/So11111111111111111111111111111111111111112/logo.png"),
    Token("USDC", "USD Coin",     "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v", 0.0, 6, logoUrl = "$CDN/EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v/logo.png"),
    Token("USDT", "Tether",       "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB", 0.0, 6, logoUrl = "https://tether.to/images/logoCircle.png"),
    Token("RAY",  "Raydium",      "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R", 0.0, 6, logoUrl = "$CDN/4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R/logo.png"),
    Token("BONK", "Bonk",         "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263", 0.0, 5, logoUrl = "https://arweave.net/hQiPZOsRZXGXBJd_82PhVdlM_hACsT_q6wqwf5cSY7I"),
    Token("JUP",  "Jupiter",      "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",  0.0, 6, logoUrl = "https://static.jup.ag/jup/icon.png"),
    Token("WIF",  "dogwifhat",    "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm", 0.0, 6, logoUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/30315.png"),
    Token("JTO",  "Jito",         "jtojtomepa8bdya6NkBeinmnwyclTDRevqkPoj17iKz",  0.0, 9, logoUrl = "https://metadata.jito.network/token/jto/image"),
    Token("PYTH", "Pyth Network", "HZ1JovNiVvGrCNiiYWY1ZZcnCxcD4UETQ5FJ3EzKP8S",  0.0, 6, logoUrl = "https://assets.coingecko.com/coins/images/31924/small/pyth.png"),
    Token("ORCA", "Orca",         "orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE",  0.0, 6, logoUrl = "$CDN/orcaEKTdK7LKz57vaAYr9QeNsVEPfiu6QeMU1kektZE/logo.png"),
    Token("MNGO", "Mango",        "MangoCzJ36AjZyKwVj3VnYU4GTonjfVEnJmvvWaxLac",  0.0, 6, logoUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/11171.png"),
    Token("SAMO", "Samoyedcoin",  "7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU", 0.0, 9, logoUrl = "$CDN/7xKXtg2CW87d97TXJSDpbD5jBkheTqA83TZRuJosgAsU/logo.png"),
    Token("STEP", "Step Finance", "StepAscQoEioFxxWGnh2sLBDFp9d8rvKz2Yp39iDpyT",  0.0, 9, logoUrl = "$CDN/StepAscQoEioFxxWGnh2sLBDFp9d8rvKz2Yp39iDpyT/logo.png"),
    Token("FIDA", "Bonfida",      "EchesyfXePKdLtoiZSL8ppeVEq2oRFpFnoD5hYKBmT9N", 0.0, 6, logoUrl = "https://assets.coingecko.com/coins/images/13395/small/bonfida.png"),
    Token("DUST", "DUST Protocol","DUSTawucrTsGU8hcqRdHDCbuYhCPADMLM2VcCb8VnFnQ", 0.0, 9, logoUrl = "https://s2.coinmarketcap.com/static/img/coins/64x64/22038.png")
)