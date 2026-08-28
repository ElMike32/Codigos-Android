package com.example.componentesapp

data class ApiResponse(
    val componentes: List<ComponenteDTO>,
    val pallet: List<EmpaqueDTO>,
    val single: List<EmpaqueDTO>
)

data class ComponenteDTO(
    val codigo: String,
    val descripcion: String,
    val cantidad: String,
    val maquina: String,
    val material: String,
    val esTipoX: Boolean
) {
    val qrData: String
        get() = if (esTipoX) "5X$codigo/$cantidad/$maquina/930" else "/$codigo/$cantidad/$maquina"

    val pieQrText: String
        get() {
            val q = cantidad.trim()
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
    fun toQrData(maquina: String) = "/$codigo/$cantidad/$maquina"
    
    val pieQrText: String
        get() {
            val q = cantidad.trim()
            if (q.isEmpty() || q == "0") return "1 PALLET"
            return when (val num = q.toIntOrNull()) {
                1 -> "1 CAJA"
                in 2..Int.MAX_VALUE -> "$num CAJAS"
                else -> "$q CAJAS"
            }
        }
}
