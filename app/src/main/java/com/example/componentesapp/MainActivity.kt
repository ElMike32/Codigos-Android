package com.example.componentesapp

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.regex.Pattern

// Modelo UI auxiliar para el dropdown de búsqueda
data class MaterialItem(
    val material: String,
    val maquina: String,
    val definicion: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFFF5F5F5)
                ) {
                    PantallaPrincipal()
                }
            }
        }
    }
}

// --- UTILIDADES ---
fun normalizarTexto(texto: String): String {
    val temp = Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD)
    val pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+")
    return pattern.matcher(temp).replaceAll("")
}

fun generarBitmapQR(datos: String): Bitmap? {
    if (datos.isEmpty()) return null
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(datos, BarcodeFormat.QR_CODE, 250, 250)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}

// --- PANTALLA PRINCIPAL ---
@Composable
fun PantallaPrincipal() {
    var apiData by remember { mutableStateOf<ApiResponse?>(null) }
    var estadoConexion by remember { mutableStateOf("Cargando datos remotos...") }
    var estaCargando by remember { mutableStateOf(true) }

    var listaMateriales by remember { mutableStateOf<List<MaterialItem>>(emptyList()) }
    var materialSeleccionado by remember { mutableStateOf<MaterialItem?>(null) }
    var textoBusqueda by remember { mutableStateOf("") }
    var mostrarSugerencias by remember { mutableStateOf(false) }

    var tabSeleccionada by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val response = ApiClient.instance.getDataFromScript(
                    "https://script.google.com/macros/s/AKfycbxRu_PdrXqqFHRL3PtCvJKkY89mu2zajbQHHGIpHWJfImxiRIbG63nM0LGzFnjwNsR6uQ/exec"
                )
                apiData = response
                
                val unicos = response.componentes
                    .filter { it.material.isNotBlank() }
                    .distinctBy { Pair(it.material, it.maquina) }
                    .map { MaterialItem(it.material, it.maquina, it.definicion) }

                listaMateriales = unicos
                if (unicos.isNotEmpty()) {
                    materialSeleccionado = unicos.first()
                    textoBusqueda = "${unicos.first().material} (${unicos.first().maquina})"
                }
                
                estadoConexion = "● Datos sincronizados"
                estaCargando = false
            } catch (e: Exception) {
                estadoConexion = "Error de conexión: ${e.localizedMessage}"
                estaCargando = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // 1. Buscador Dinámico
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                OutlinedTextField(
                    value = textoBusqueda,
                    onValueChange = { query ->
                        textoBusqueda = query
                        mostrarSugerencias = query.isNotBlank()
                    },
                    label = { Text("🔍 Buscar Material o Máquina...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                AnimatedVisibility(visible = mostrarSugerencias) {
                    val queryNorm = normalizarTexto(textoBusqueda)
                    val coincidencias = listaMateriales.filter { item ->
                        val eval = "${item.material} ${item.maquina} ${item.definicion}"
                        normalizarTexto(eval).contains(queryNorm)
                    }.take(15)

                    Card(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).padding(top = 4.dp),
                        border = BorderStroke(1.dp, Color(0xFF1F4E79)),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        LazyColumn {
                            items(coincidencias) { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            materialSeleccionado = item
                                            textoBusqueda = "${item.material} (${item.maquina})"
                                            mostrarSugerencias = false
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = "${item.material}  │  Máq: ${item.maquina}" + 
                                                if (item.definicion.isNotBlank()) " (${item.definicion})" else "",
                                        fontSize = 13.sp,
                                        color = Color.Black
                                    )
                                }
                                HorizontalDivider(color = Color(0xFFEEEEEE))
                            }
                        }
                    }
                }

                Text(
                    text = estadoConexion,
                    fontSize = 11.sp,
                    color = if (estadoConexion.contains("●")) Color(0xFF2E7D32) else Color.Red,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // 2. Encabezado del Material Seleccionado
        materialSeleccionado?.let { mat ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F0FA)),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DESCRIPCIÓN:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F4E79)
                        )
                        Text(
                            text = if (mat.definicion.isNotBlank()) mat.definicion else "Sin descripción registrada",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Máquina: ${mat.maquina}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1F4E79)
                        )
                        Text(
                            text = "Material: ${mat.material}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF111111)
                        )
                    }
                }
            }
        }

        // 3. Pestañas
        TabRow(selectedTabIndex = tabSeleccionada, containerColor = Color.White) {
            Tab(selected = tabSeleccionada == 0, onClick = { tabSeleccionada = 0 }) {
                Text("Componentes", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
            Tab(selected = tabSeleccionada == 1, onClick = { tabSeleccionada = 1 }) {
                Text("Empaquetado", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (estaCargando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            materialSeleccionado?.let { mat ->
                when (tabSeleccionada) {
                    0 -> VistaComponentes(apiData?.componentes ?: emptyList(), mat)
                    1 -> VistaEmpaquetado(apiData?.pallet ?: emptyList(), apiData?.single ?: emptyList(), mat)
                }
            }
        }
    }
}

// --- VISTAS ---
@Composable
fun VistaComponentes(componentes: List<ComponenteDTO>, mat: MaterialItem) {
    val filtrados = componentes.filter { it.material == mat.material && it.maquina == mat.maquina }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        item {
            Text(
                text = "━ COMPONENTES DE MÁQUINA ━",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F4E79),
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )
        }

        items(filtrados) { comp ->
            TarjetaElemento(
                codigo = comp.codigo,
                descripcion = comp.descripcion,
                pieQr = comp.pieQrText,
                datosQr = comp.qrData
            )
        }
    }
}

@Composable
fun VistaEmpaquetado(pallet: List<EmpaqueDTO>, single: List<EmpaqueDTO>, mat: MaterialItem) {
    val palletFiltrado = pallet.filter { it.materialRef == mat.material }
    val singleFiltrado = single.filter { it.materialRef == mat.material }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(32.dp),
        contentPadding = PaddingValues(bottom = 60.dp)
    ) {
        if (palletFiltrado.isNotEmpty()) {
            item {
                Text(
                    text = "━ PALLET PACKAGING ━",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F4E79),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(palletFiltrado) { itemP ->
                TarjetaElemento(
                    codigo = itemP.codigo,
                    descripcion = itemP.descripcion,
                    pieQr = itemP.pieQrTextPallet,
                    datosQr = itemP.toQrData(mat.maquina)
                )
            }
        }

        if (singleFiltrado.isNotEmpty()) {
            item {
                Text(
                    text = "━ SINGLE PACKAGING ━",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F4E79),
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(singleFiltrado) { itemS ->
                TarjetaElemento(
                    codigo = itemS.codigo,
                    descripcion = itemS.descripcion,
                    pieQr = itemS.pieQrTextSingle,
                    datosQr = itemS.toQrData(mat.maquina)
                )
            }
        }
    }
}

@Composable
fun TarjetaElemento(
    codigo: String,
    descripcion: String,
    pieQr: String,
    datosQr: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF111111), RoundedCornerShape(6.dp)),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = codigo,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = descripcion,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val qrBitmap = remember(datosQr) { generarBitmapQR(datosQr) }
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Código QR",
                        modifier = Modifier.size(90.dp)
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = pieQr,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }
        }
    }
}
