package com.example.componentesapp

data class ApiResponse(
    val componentes: List<ComponenteDTO> = emptyList(),
    val pallet: List<EmpaqueDTO> = emptyList(),
    val single: List<EmpaqueDTO> = emptyList()
)

data class ComponenteDTO(
    val codigo: String,
    val descripcion: String,
    val cantidad: String,
    val maquina: String,
    val material: String,
    val definicion: String = "",
    val esTipoX: Boolean
) {
    // Formato exacto de QR según la bandera esTipoX
    val qrData: String
        get() {
            val q = cantidad.trim().let { if (it.endsWith(".0")) it.dropLast(2) else it }
            return if (esTipoX) "5X$codigo/$q/$maquina/930" else "/$codigo/$q/$maquina"
        }

    val pieQrText: String
        get() {
            val q = cantidad.trim().let { if (it.endsWith(".0")) it.dropLast(2) else it }
            if (q.isEmpty() || q == "0") return "1 PALLET"
            return when (val num = q.toIntOrNull()) {
                1 -> "1 CAJA"
                in 2..Int.MAX_VALUE -> "$num PIEZAS"
                else -> "$q PIEZAS"
            }
        }
}

data class EmpaqueDTO(
    val codigo: String,
    val descripcion: String,
    val cantidad: String,
    val materialRef: String
) {
    // Agregado el prefijo '1X' requerido por el estándar del Python
    fun toQrData(maquina: String): String {
        val q = cantidad.trim().let { if (it.endsWith(".0")) it.dropLast(2) else it }
        return "1X$codigo/$q/$maquina"
    }

    val pieQrTextPallet: String = "1 PALLET"

    val pieQrTextSingle: String
        get() {
            val q = cantidad.trim().let { if (it.endsWith(".0")) it.dropLast(2) else it }
            return when (val num = q.toIntOrNull()) {
                null -> if (q.isNotEmpty()) "$q CAJAS" else "0 CAJAS"
                else -> "$num CAJAS"
            }
        }
}
