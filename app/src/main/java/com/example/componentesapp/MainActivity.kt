package com.example.componentesapp

import android.graphics.Bitmap
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.regex.Pattern

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
    val context = LocalContext.current
    val localStorage = remember { LocalStorage(context) }
    val coroutineScope = rememberCoroutineScope()

    // Obtener ID único de hardware del teléfono
    val deviceId = remember { 
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_DEVICE"
    }

    var apiData by remember { mutableStateOf<ApiResponse?>(null) }
    var estadoConexion by remember { mutableStateOf("Cargando...") }
    var estaCargando by remember { mutableStateOf(true) }

    var listaMateriales by remember { mutableStateOf<List<MaterialItem>>(emptyList()) }
    var materialSeleccionado by remember { mutableStateOf<MaterialItem?>(null) }
    var textoBusqueda by remember { mutableStateOf("") }
    
    var buscadorExpandido by remember { mutableStateOf(false) }
    var mostrarSugerencias by remember { mutableStateOf(false) }
    var tabSeleccionada by remember { mutableIntStateOf(0) }

    fun cargarModelosEnUI(response: ApiResponse) {
        val unicos = response.componentes
            .filter { it.material.isNotBlank() }
            .distinctBy { Pair(it.material.trim(), it.maquina.trim()) }
            .map { 
                MaterialItem(
                    material = it.material.trim(), 
                    maquina = it.maquina.trim(), 
                    definicion = it.definicion.trim()
                ) 
            }

        listaMateriales = unicos
        if (unicos.isNotEmpty() && materialSeleccionado == null) {
            materialSeleccionado = unicos.first()
        }
    }

    fun sincronizarDesdeNube() {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                estaCargando = true
                estadoConexion = "Verificando acceso..."
            }
            try {
                // Se envía el ANDROID_ID en la URL a Apps Script
                val urlConId = "https://script.google.com/macros/s/AKfycbxRu_PdrXqqFHRL3PtCvJKkY89mu2zajbQHHGIpHWJfImxiRIbG63nM0LGzFnjwNsR6uQ/exec"
                val response = ApiClient.instance.getDataFromScript(urlConId)

                withContext(Dispatchers.Main) {
                    when (response.status) {
                        "OK" -> {
                            val jsonString = Gson().toJson(response)
                            localStorage.guardarJson(jsonString)
                            apiData = response
                            cargarModelosEnUI(response)
                            estadoConexion = "● Memoria sincronizada"
                        }
                        "WIPE_AND_BLOCK" -> {
                            // BORRADO REMOTO: Se elimina la base local y se limpia la UI
                            localStorage.limpiarMemoriaLocal()
                            apiData = null
                            listaMateriales = emptyList()
                            materialSeleccionado = null
                            estadoConexion = "⛔ ACCESO REVOCADO REMOTAMENTE"
                        }
                        "PENDIENTE" -> {
                            apiData = null
                            listaMateriales = emptyList()
                            materialSeleccionado = null
                            estadoConexion = "⌛ Dispositivo pendiente de autorización"
                        }
                        else -> {
                            estadoConexion = "Respuesta desconocida del servidor"
                        }
                    }
                    estaCargando = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (localStorage.tieneDatos()) {
                        val jsonLocal = localStorage.obtenerJson()
                        val datosLocal = Gson().fromJson(jsonLocal, ApiResponse::class.java)
                        apiData = datosLocal
                        cargarModelosEnUI(datosLocal)
                        estadoConexion = "● Modo Offline (Sin Red)"
                    } else {
                        estadoConexion = "Error de conexión con la red"
                    }
                    estaCargando = false
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (localStorage.tieneDatos()) {
            try {
                val jsonLocal = localStorage.obtenerJson()
                val datosLocal = Gson().fromJson(jsonLocal, ApiResponse::class.java)
                apiData = datosLocal
                cargarModelosEnUI(datosLocal)
                estadoConexion = "● Datos Locales (Offline)"
                estaCargando = false
            } catch (e: Exception) {
                sincronizarDesdeNube()
            }
        } else {
            sincronizarDesdeNube()
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
        
        // 1. Buscador Desplegable
        if (buscadorExpandido) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(6.dp)) {
                    OutlinedTextField(
                        value = textoBusqueda,
                        onValueChange = { query ->
                            textoBusqueda = query
                            mostrarSugerencias = query.isNotBlank()
                        },
                        label = { Text("Buscar Material o Máquina...") },
                        trailingIcon = {
                            IconButton(onClick = { 
                                buscadorExpandido = false 
                                mostrarSugerencias = false
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Cerrar")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    textoBusqueda = ""
                                }
                            },
                        singleLine = true
                    )

                    AnimatedVisibility(visible = mostrarSugerencias) {
                        val queryNorm = normalizarTexto(textoBusqueda)
                        val coincidencias = listaMateriales.filter { item ->
                            val eval = "${item.material} ${item.maquina} ${item.definicion}"
                            normalizarTexto(eval).contains(queryNorm)
                        }.take(15)

                        Card(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(top = 4.dp),
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
                                                buscadorExpandido = false
                                                mostrarSugerencias = false
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            text = "${item.material}  │  Máq: ${item.maquina}" + 
                                                    if (item.definicion.isNotBlank()) " (${item.definicion})" else "",
                                            fontSize = 12.sp,
                                            color = Color.Black
                                        )
                                    }
                                    HorizontalDivider(color = Color(0xFFEEEEEE))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Encabezado de Material
        materialSeleccionado?.let { mat ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE6F0FA)),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = estadoConexion,
                            fontSize = 10.sp,
                            color = if (estadoConexion.contains("●")) Color(0xFF2E7D32) else Color.Red
                        )
                        
                        Row {
                            IconButton(
                                onClick = { sincronizarDesdeNube() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Sincronizar", tint = Color(0xFF1F4E79))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { 
                                    buscadorExpandido = !buscadorExpandido
                                    textoBusqueda = ""
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Buscar", tint = Color(0xFF1F4E79))
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DESCRIPCIÓN:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F4E79)
                            )
                            Text(
                                text = if (mat.definicion.isNotBlank()) mat.definicion else "Sin descripción registrada",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Máquina: ${mat.maquina}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1F4E79)
                            )
                            Text(
                                text = "Material: ${mat.material}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF111111)
                            )
                        }
                    }
                }
            }
        }

        // 3. Pestañas
        TabRow(selectedTabIndex = tabSeleccionada, containerColor = Color.White) {
            Tab(selected = tabSeleccionada == 0, onClick = { tabSeleccionada = 0 }) {
                Text("Componentes", modifier = Modifier.padding(6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Tab(selected = tabSeleccionada == 1, onClick = { tabSeleccionada = 1 }) {
                Text("Empaquetado", modifier = Modifier.padding(6.dp), fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (estaCargando) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (apiData == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = estadoConexion,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
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

// --- VISTAS DE TARJETAS ---
@Composable
fun VistaComponentes(componentes: List<ComponenteDTO>, mat: MaterialItem) {
    val filtrados = componentes.filter { 
        it.material.trim() == mat.material.trim() && 
        it.maquina.trim() == mat.maquina.trim() 
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(180.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        item {
            Text(
                text = "━ COMPONENTES DE MÁQUINA ━",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1F4E79),
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                textAlign = TextAlign.Center
            )
        }

        items(filtrados) { comp ->
            TarjetaElemento(
                tituloGrande = comp.codigo,
                descripcion = comp.descripcion,
                pieQr = comp.pieQrText,
                datosQr = comp.qrData
            )
        }
    }
}

@Composable
fun VistaEmpaquetado(pallet: List<EmpaqueDTO>, single: List<EmpaqueDTO>, mat: MaterialItem) {
    val palletFiltrado = pallet.filter { it.materialRef.trim() == mat.material.trim() }
    val singleFiltrado = single.filter { it.materialRef.trim() == mat.material.trim() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(180.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp)
    ) {
        if (palletFiltrado.isNotEmpty()) {
            item {
                Text(
                    text = "━ PALLET PACKAGING ━",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F4E79),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(palletFiltrado) { itemP ->
                TarjetaElemento(
                    tituloGrande = itemP.codigo,
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
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1F4E79),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
            items(singleFiltrado) { itemS ->
                TarjetaElemento(
                    tituloGrande = itemS.codigo,
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
    tituloGrande: String,
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
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = tituloGrande.trim(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                if (descripcion.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = descripcion.trim(),
                        fontSize = 13.sp,
                        color = Color(0xFF333333),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val qrBitmap = remember(datosQr) { generarBitmapQR(datosQr) }
                qrBitmap?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Código QR",
                        modifier = Modifier.size(85.dp)
                    )
                }
                if (pieQr.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = pieQr,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}
