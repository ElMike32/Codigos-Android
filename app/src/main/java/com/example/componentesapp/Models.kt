package com.example.componentesapp

data class ApiResponse(
    val componentes: List<ComponenteDTO> = emptyList(),
    val pallet: List<EmpaqueDTO> = emptyList(),
    val single: List<EmpaqueDTO> = emptyList()
)

data class ComponenteDTO(
    val codigo: String = "",
    val descripcion: String = "",
    val cantidad: String = "",
    val maquina: String = "",
    val material: String = "",
    val definicion: String = "",
    val esTipoX: Boolean = false
) {
    val cantLimpia: String
        get() {
            val q = cantidad.trim()
            return if (q.endsWith(".0")) q.dropLast(2) else q
        }

    val qrData: String
        get() {
            val cod = codigo.trim()
            val maq = maquina.trim()
            return if (esTipoX) "5X$cod/$cantLimpia/$maq/930" else "/$cod/$cantLimpia/$maq"
        }

    val pieQrText: String
        get() {
            if (cantLimpia.isEmpty() || cantLimpia == "0") return "1 PALLET"
            return when (val num = cantLimpia.toIntOrNull()) {
                1 -> "1 CAJA"
                in 2..Int.MAX_VALUE -> "$num PIEZAS"
                else -> "$cantLimpia PIEZAS"
            }
        }
}

data class EmpaqueDTO(
    val codigo: String = "",
    val descripcion: String = "",
    val cantidad: String = "",
    val materialRef: String = ""
) {
    val cantLimpia: String
        get() {
            val q = cantidad.trim()
            return if (q.endsWith(".0")) q.dropLast(2) else q
        }

    fun toQrData(maquina: String): String = "1X${codigo.trim()}/$cantLimpia/${maquina.trim()}"

    val pieQrTextPallet: String = "1 PALLET"

    val pieQrTextSingle: String
        get() {
            return when (val num = cantLimpia.toIntOrNull()) {
                null -> if (cantLimpia.isNotEmpty()) "$cantLimpia CAJAS" else "0 CAJAS"
                else -> "$num CAJAS"
            }
        }
}
