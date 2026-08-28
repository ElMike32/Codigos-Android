package com.example.componentesapp

import com.google.gson.annotations.SerializedName

data class ApiResponse(
    val componentes: List<ComponenteDTO> = emptyList(),
    val pallet: List<EmpaqueDTO> = emptyList(),
    val single: List<EmpaqueDTO> = emptyList()
)

data class ComponenteDTO(
    @SerializedName("componente") val codigoComponente: String? = "",
    @SerializedName("descripcion") val descripcionComponente: String? = "",
    @SerializedName("cantidad") val cantidad: String? = "",
    @SerializedName("maquina") val maquina: String? = "",
    @SerializedName("material") val material: String? = "",
    @SerializedName("definición") val definicionMaterialAlt: String? = null,
    @SerializedName("definicion") val definicionMaterial: String? = null,
    @SerializedName("X?") val esTipoXAlt: Boolean? = false,
    @SerializedName("esTipoX") val esTipoX: Boolean? = false
) {
    // Definición general del material (R1M3...)
    val definicionReal: String
        get() = (definicionMaterial ?: definicionMaterialAlt).orEmpty().trim()

    val esX: Boolean
        get() = (esTipoX ?: esTipoXAlt) ?: false

    val codigoCompLimpio: String
        get() = codigoComponente.orEmpty().trim()

    val descCompLimpio: String
        get() = descripcionComponente.orEmpty().trim()

    val cantLimpia: String
        get() {
            val q = cantidad.orEmpty().trim()
            return if (q.endsWith(".0")) q.dropLast(2) else q
        }

    // QR usa la columna 'componente' (ej. 3991)
    val qrData: String
        get() {
            val maq = maquina.orEmpty().trim()
            return if (esX) "5X$codigoCompLimpio/$cantLimpia/$maq/930" else "/$codigoCompLimpio/$cantLimpia/$maq"
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
    @SerializedName("codigo") val codigoAlt: String? = null,
    @SerializedName("componente") val codigoComp: String? = null,
    @SerializedName("descripcion") val descripcion: String? = "",
    @SerializedName("cantidad") val cantidad: String? = "",
    @SerializedName("materialRef") val materialRefAlt: String? = null,
    @SerializedName("material") val material: String? = null
) {
    val codigoEmpaque: String
        get() = (codigoAlt ?: codigoComp).orEmpty().trim()

    val matRef: String
        get() = (materialRefAlt ?: material).orEmpty().trim()

    val descEmpaque: String
        get() = descripcion.orEmpty().trim()

    val cantLimpia: String
        get() {
            val q = cantidad.orEmpty().trim()
            return if (q.endsWith(".0")) q.dropLast(2) else q
        }

    fun toQrData(maquina: String): String = "1X$codigoEmpaque/$cantLimpia/${maquina.trim()}"

    val pieQrTextPallet: String = "1 PALLET"

    val pieQrTextSingle: String
        get() {
            return when (val num = cantLimpia.toIntOrNull()) {
                null -> if (cantLimpia.isNotEmpty()) "$cantLimpia CAJAS" else "0 CAJAS"
                else -> "$num CAJAS"
            }
        }
}
