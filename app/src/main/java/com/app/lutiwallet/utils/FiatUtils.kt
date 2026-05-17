package com.app.lutiwallet.utils

import java.util.Locale


fun Double.format(digits: Int): String = String.format(Locale.US, "%.${digits}f", this)


const val DIRECCION_TESORERIA = "8tsgkjVPydnRjbcBGo7TjvmEaaLtRP4mY51MhwF5xLGH"
const val COMISION_PORCENTAJE = 0.001
const val MONTO_MINIMO_ENVIO = 0.001
const val VERSION_INSTALADA = "1.1"


val MONEDAS_FIAT = listOf("USD", "ARS", "BRL", "PYG", "EUR", "CNY", "INR", "AED", "GBP", "MXN")


fun obtenerFactorFiat(moneda: String, precioDolarCrypto: Double): Double {
    return when (moneda) {
        "ARS" -> precioDolarCrypto
        "BRL" -> 5.15
        "PYG" -> 7400.0
        "EUR" -> 0.92
        "CNY" -> 7.23
        "INR" -> 83.3
        "AED" -> 3.67
        "GBP" -> 0.79
        "MXN" -> 16.5
        else  -> 1.0
    }
}


val TEXTOS_IDIOMAS = mapOf(
    "Español" to mapOf(
        // Config
        "titulo" to "CONFIGURACIÓN", "frase" to "Ver Frase Semilla", "idioma" to "Idioma",
        "pass" to "Contraseña", "ver_frase" to "VER FRASE", "cerrar" to "CERRAR",
        "politica" to "Política de Privacidad",
        // Login
        "login" to "INTRODUCE TU CLAVE", "desbloquear" to "DESBLOQUEAR",
        // Balance
        "balance" to "BALANCE TOTAL",
        // Nav
        "nav_billetera" to "Billetera", "nav_tokens" to "Tokens",
        "nav_conversion" to "Conversión", "nav_chat" to "LutiChat",
        // Billetera
        "mis_activos" to "MIS ACTIVOS", "historial" to "HISTORIAL DE ACTIVIDAD",
        "recibir" to "Recibir", "enviar" to "Enviar",
        "escanear" to "Escanear", "retirar" to "Retirar", "volver" to "Volver",
        "enviar_sol" to "ENVIAR SOL", "confirmar_envio" to "CONFIRMAR ENVÍO",
        "continuar" to "CONTINUAR", "confirmar" to "CONFIRMAR", "cancelar" to "CANCELAR",
        "recibir_sol" to "RECIBIR SOL",
        "resumen" to "RESUMEN", "comisiones" to "COMISIONES",
        "procesando" to "Procesando envío...",
        // Tokens
        "mis_tokens" to "MIS TOKENS", "en_wallet" to "EN TU WALLET",
        "favoritos" to "FAVORITOS", "populares" to "TOKENS POPULARES",
        "resultados" to "RESULTADOS",
        "sin_resultados" to "Sin resultados para",
        "buscar_placeholder" to "Buscar token por nombre o mint...",
        "swap" to "Swap",
        "sol_a_moneda" to "SOL → Moneda", "moneda_a_sol" to "Moneda → SOL"
    ),
    "Inglés" to mapOf(
        "titulo" to "SETTINGS", "frase" to "View Seed Phrase", "idioma" to "Language",
        "pass" to "Password", "ver_frase" to "VIEW PHRASE", "cerrar" to "CLOSE",
        "politica" to "Privacy Policy",
        "login" to "ENTER PASSWORD", "desbloquear" to "UNLOCK",
        "balance" to "TOTAL BALANCE",
        "nav_billetera" to "Wallet", "nav_tokens" to "Tokens",
        "nav_conversion" to "Convert", "nav_chat" to "LutiChat",
        "mis_activos" to "MY ASSETS", "historial" to "ACTIVITY HISTORY",
        "recibir" to "Receive", "enviar" to "Send",
        "escanear" to "Scan", "retirar" to "Withdraw", "volver" to "Back",
        "enviar_sol" to "SEND SOL", "confirmar_envio" to "CONFIRM SEND",
        "continuar" to "CONTINUE", "confirmar" to "CONFIRM", "cancelar" to "CANCEL",
        "recibir_sol" to "RECEIVE SOL",
        "resumen" to "SUMMARY", "comisiones" to "FEES",
        "procesando" to "Processing...",
        "mis_tokens" to "MY TOKENS", "en_wallet" to "IN YOUR WALLET",
        "favoritos" to "FAVORITES", "populares" to "POPULAR TOKENS",
        "resultados" to "RESULTS",
        "sin_resultados" to "No results for",
        "buscar_placeholder" to "Search token by name or mint...",
        "swap" to "Swap",
        "sol_a_moneda" to "SOL → Currency", "moneda_a_sol" to "Currency → SOL"
    ),
    "Chino" to mapOf(
        "titulo" to "设置", "frase" to "查看助记词", "idioma" to "语言",
        "pass" to "密码", "ver_frase" to "查看助记词", "cerrar" to "关闭",
        "politica" to "隐私政策",
        "login" to "输入密码", "desbloquear" to "解锁",
        "balance" to "总余额",
        "nav_billetera" to "钱包", "nav_tokens" to "代币",
        "nav_conversion" to "转换", "nav_chat" to "聊天",
        "mis_activos" to "我的资产", "historial" to "交易历史",
        "recibir" to "接收", "enviar" to "发送",
        "escanear" to "扫描", "retirar" to "提取", "volver" to "返回",
        "enviar_sol" to "发送 SOL", "confirmar_envio" to "确认发送",
        "continuar" to "继续", "confirmar" to "确认", "cancelar" to "取消",
        "recibir_sol" to "接收 SOL",
        "resumen" to "摘要", "comisiones" to "费用",
        "procesando" to "处理中...",
        "mis_tokens" to "我的代币", "en_wallet" to "在你的钱包",
        "favoritos" to "收藏", "populares" to "热门代币",
        "resultados" to "结果",
        "sin_resultados" to "未找到",
        "buscar_placeholder" to "按名称或地址搜索代币...",
        "swap" to "兑换",
        "sol_a_moneda" to "SOL → 货币", "moneda_a_sol" to "货币 → SOL"
    ),
    "Portugués" to mapOf(
        "titulo" to "CONFIGURAÇÕES", "frase" to "Ver Frase Semente", "idioma" to "Idioma",
        "pass" to "Senha", "ver_frase" to "VER FRASE", "cerrar" to "FECHAR",
        "politica" to "Política de Privacidade",
        "login" to "DIGITE SUA SENHA", "desbloquear" to "DESBLOQUEAR",
        "balance" to "SALDO TOTAL",
        "nav_billetera" to "Carteira", "nav_tokens" to "Tokens",
        "nav_conversion" to "Conversão", "nav_chat" to "LutiChat",
        "mis_activos" to "MEUS ATIVOS", "historial" to "HISTÓRICO",
        "recibir" to "Receber", "enviar" to "Enviar",
        "escanear" to "Escanear", "retirar" to "Retirar", "volver" to "Voltar",
        "enviar_sol" to "ENVIAR SOL", "confirmar_envio" to "CONFIRMAR ENVIO",
        "continuar" to "CONTINUAR", "confirmar" to "CONFIRMAR", "cancelar" to "CANCELAR",
        "recibir_sol" to "RECEBER SOL",
        "resumen" to "RESUMO", "comisiones" to "TAXAS",
        "procesando" to "Processando...",
        "mis_tokens" to "MEUS TOKENS", "en_wallet" to "NA SUA CARTEIRA",
        "favoritos" to "FAVORITOS", "populares" to "TOKENS POPULARES",
        "resultados" to "RESULTADOS",
        "sin_resultados" to "Sem resultados para",
        "buscar_placeholder" to "Buscar token por nome ou mint...",
        "swap" to "Trocar",
        "sol_a_moneda" to "SOL → Moeda", "moneda_a_sol" to "Moeda → SOL"
    ),
    "Hindi" to mapOf(
        "titulo" to "सेटिंग्स", "frase" to "सीड फ्रेज देखें", "idioma" to "भाषा",
        "pass" to "पासवर्ड", "ver_frase" to "फ्रेज देखें", "cerrar" to "बंद करें",
        "politica" to "गोपनीयता नीति",
        "login" to "पासवर्ड दर्ज करें", "desbloquear" to "अनलॉक",
        "balance" to "कुल शेष",
        "nav_billetera" to "वॉलेट", "nav_tokens" to "टोकन",
        "nav_conversion" to "रूपांतरण", "nav_chat" to "चैट",
        "mis_activos" to "मेरी संपत्ति", "historial" to "गतिविधि इतिहास",
        "recibir" to "प्राप्त करें", "enviar" to "भेजें",
        "escanear" to "स्कैन", "retirar" to "निकालें", "volver" to "वापस",
        "enviar_sol" to "SOL भेजें", "confirmar_envio" to "भेजना पुष्टि करें",
        "continuar" to "जारी रखें", "confirmar" to "पुष्टि करें", "cancelar" to "रद्द करें",
        "recibir_sol" to "SOL प्राप्त करें",
        "resumen" to "सारांश", "comisiones" to "शुल्क",
        "procesando" to "प्रसंस्करण...",
        "mis_tokens" to "मेरे टोकन", "en_wallet" to "आपके वॉलेट में",
        "favoritos" to "पसंदीदा", "populares" to "लोकप्रिय टोकन",
        "resultados" to "परिणाम",
        "sin_resultados" to "कोई परिणाम नहीं",
        "buscar_placeholder" to "नाम या mint से टोकन खोजें...",
        "swap" to "स्वैप",
        "sol_a_moneda" to "SOL → मुद्रा", "moneda_a_sol" to "मुद्रा → SOL"
    ),
    "Árabe" to mapOf(
        "titulo" to "إعدادات", "frase" to "عرض عبارة البذور", "idioma" to "لغة",
        "pass" to "كلمة المرور", "ver_frase" to "عرض العبارة", "cerrar" to "إغلاق",
        "politica" to "سياسة الخصوصية",
        "login" to "أدخل كلمة المرور", "desbloquear" to "فتح",
        "balance" to "الرصيد الإجمالي",
        "nav_billetera" to "المحفظة", "nav_tokens" to "الرموز",
        "nav_conversion" to "التحويل", "nav_chat" to "دردشة",
        "mis_activos" to "أصولي", "historial" to "سجل النشاط",
        "recibir" to "استلام", "enviar" to "إرسال",
        "escanear" to "مسح", "retirar" to "سحب", "volver" to "عودة",
        "enviar_sol" to "إرسال SOL", "confirmar_envio" to "تأكيد الإرسال",
        "continuar" to "متابعة", "confirmar" to "تأكيد", "cancelar" to "إلغاء",
        "recibir_sol" to "استلام SOL",
        "resumen" to "ملخص", "comisiones" to "الرسوم",
        "procesando" to "جارٍ المعالجة...",
        "mis_tokens" to "رموزي", "en_wallet" to "في محفظتك",
        "favoritos" to "المفضلة", "populares" to "الرموز الشائعة",
        "resultados" to "النتائج",
        "sin_resultados" to "لا نتائج لـ",
        "buscar_placeholder" to "ابحث عن رمز بالاسم أو العنوان...",
        "swap" to "تبادل",
        "sol_a_moneda" to "SOL → عملة", "moneda_a_sol" to "عملة → SOL"
    )
)

private const val CDN = "https://cdn.jsdelivr.net/gh/solana-labs/token-list@main/assets/mainnet"

val TOKEN_METADATA_FALLBACK = mapOf(
    "So11111111111111111111111111111111111111112" to Pair("SOL", "$CDN/So11111111111111111111111111111111111111112/logo.png"),
    "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" to Pair("USDC", "$CDN/EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v/logo.png"),
    "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB" to Pair("USDT", "$CDN/Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB/logo.png"),
    "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263" to Pair("BONK", "https://arweave.net/hQiPZOsRZXGXBJd_82PhVdlM_hACsT_q6wqwf5cSY7I"),
    "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R" to Pair("RAY", "$CDN/4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R/logo.png"),
    "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN" to Pair("JUP", "https://static.jup.ag/jup/icon.png"),
    "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm" to Pair("WIF", "https://bafkreibk3covs5ltyqxa272uodhculbgn2zm52cygvel2ista7briqxiii.ipfs.nftstorage.link"),
    "jtojtomepa8bdya6NkBeinmnwyclTDRevqkPoj17iKz" to Pair("JTO", "https://metadata.jito.network/token/jto/image")
)

fun obtenerFavoritos(context: android.content.Context): Set<String> {
    val prefs = context.getSharedPreferences("lutiwallet_favoritos", android.content.Context.MODE_PRIVATE)
    return prefs.getStringSet("favoritos", setOf(
        "So11111111111111111111111111111111111111112",
        "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
        "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
        "DezXAZ8z7PnrnRJjz3wXBoRgixCa6xjnB7YaB1pPB263",
        "4k3Dyjzvzp8eMZWUXbBCjEvwSkkk59S5iCNLY3QrkX6R",
        "JUPyiwrYJFskUPiHa7hkeR8VUtAeFoSYbKedZNsDvCN",
        "EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm",
        "jtojtomepa8bdya6NkBeinmnwyclTDRevqkPoj17iKz"
    )) ?: emptySet()
}

fun toggleFavorito(context: android.content.Context, mintAddress: String): Boolean {
    val prefs = context.getSharedPreferences("lutiwallet_favoritos", android.content.Context.MODE_PRIVATE)
    val favoritos = obtenerFavoritos(context).toMutableSet()
    val eraFavorito = favoritos.contains(mintAddress)
    if (eraFavorito) favoritos.remove(mintAddress)
    else favoritos.add(mintAddress)
    prefs.edit().putStringSet("favoritos", favoritos).apply()
    return !eraFavorito
}

fun guardarTokenFavorito(context: android.content.Context, token: com.app.lutiwallet.modelo.Token) {
    val prefs = context.getSharedPreferences("lutiwallet_favoritos", android.content.Context.MODE_PRIVATE)
    // Guardar datos del token como "mint|symbol|name|logoUrl"
    val tokensGuardados = prefs.getStringSet("tokens_data", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
    tokensGuardados.removeIf { it.startsWith(token.mintAddress + "|") }
    tokensGuardados.add("${token.mintAddress}|${token.symbol}|${token.name}|${token.logoUrl}")
    prefs.edit().putStringSet("tokens_data", tokensGuardados).apply()
}

fun obtenerTokensFavoritos(context: android.content.Context): List<com.app.lutiwallet.modelo.Token> {
    val prefs = context.getSharedPreferences("lutiwallet_favoritos", android.content.Context.MODE_PRIVATE)
    val favoritos = obtenerFavoritos(context)
    val tokensData = prefs.getStringSet("tokens_data", emptySet()) ?: emptySet()
    return tokensData.mapNotNull { data ->
        val parts = data.split("|")
        if (parts.size >= 4 && parts[0] in favoritos) {
            com.app.lutiwallet.modelo.Token(
                symbol = parts[1],
                name = parts[2],
                mintAddress = parts[0],
                balance = 0.0,
                decimals = 6,
                logoUrl = parts[3]
            )
        } else null
    }
}
