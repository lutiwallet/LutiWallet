package com.app.lutiwallet.modelo

data class Usuario(
    val idUsuario: String = "", // Este es el alias, ej: "luti.dev"
    val saldo: Double = 0.0,
    val fechaCreacion: Long = System.currentTimeMillis()
)