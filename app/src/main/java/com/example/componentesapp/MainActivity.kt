package com.example.componentesapp

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch

// COLOCA AQUÍ LA URL QUE TE DIO APPS SCRIPT AL IMPLEMENTAR
const val URL_APPS_SCRIPT = "https://script.google.com/macros/s/AKfycbxRu_PdrXqqFHRL3PtCvJKkY89mu2zajbQHHGIpHWJfImxiRIbG63nM0LGzFnjwNsR6uQ/exec"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainAppScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Componentes", "Empaquetado")

    var dataResponse by remember { mutableStateOf<ApiResponse?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    fun cargarDatos() {
        isLoading = true
        errorMessage = ""
        coroutineScope.launch {
            try {
                dataResponse = ApiClient.instance.getDataFromScript(URL_APPS_SCRIPT)
            } catch (e: Exception) {
                errorMessage = "Error al conectar: ${e.localizedMessage}"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        cargarDatos()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Visor de Componentes", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { cargarDatos() }) {
                            Text("🔄")
                        }
                    }
                )
                TabRow(selectedTabIndex = selectedTabIndex) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator()
            } else if (errorMessage.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { cargarDatos() }) { Text("Reintentar") }
                }
            } else {
                dataResponse?.let { data ->
                    when (selectedTabIndex) {
                        0 -> ComponentesTabContent(data.componentes)
                        1 -> EmpaquetadoTabContent(data.pallet, data.single)
                    }
                }
            }
        }
    }
}

@Composable
fun ComponentesTabContent(items: List<ComponenteDTO>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        items(items) { item ->
            TarjetaUI(
                codigo = item.codigo,
                descripcion = item.descripcion,
                pieQr = item.pieQrText,
                qrString = item.qrData,
                qrAlaDerecha = true
            )
        }
    }
}

@Composable
fun EmpaquetadoTabContent(palletItems: List<EmpaqueDTO>, singleItems: List<EmpaqueDTO>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Text("━ PALLET PACKAGING ━", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        items(palletItems) { item ->
            TarjetaUI(
                codigo = item.codigo,
                descripcion = item.descripcion,
                pieQr = item.pieQrText,
                qrString = item.toQrData("MAQ"),
                qrAlaDerecha = true
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item { Text("━ SINGLE PACKAGING ━", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) }
        items(singleItems) { item ->
            TarjetaUI(
                codigo = item.codigo,
                descripcion = item.descripcion,
                pieQr = item.pieQrText,
                qrString = item.toQrData("MAQ"),
                qrAlaDerecha = false
            )
        }
    }
}

@Composable
fun TarjetaUI(
    codigo: String,
    descripcion: String,
    pieQr: String,
    qrString: String,
    qrAlaDerecha: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val qrBitmap = remember(qrString) { generarQrBitmap(qrString) }

            val textContent: @Composable RowScope.() -> Unit = {
                Column(modifier = Modifier.weight(1f)) {
                    Text(codigo, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(descripcion, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            val qrContent: @Composable () -> Unit = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "QR",
                            modifier = Modifier.size(85.dp)
                        )
                    }
                    Text(pieQr, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (qrAlaDerecha) {
                textContent()
                Spacer(modifier = Modifier.width(12.dp))
                qrContent()
            } else {
                qrContent()
                Spacer(modifier = Modifier.width(12.dp))
                textContent()
            }
        }
    }
}

fun generarQrBitmap(contenido: String): Bitmap? {
    if (contenido.isEmpty()) return null
    return try {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(contenido, BarcodeFormat.QR_CODE, 200, 200)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        bmp
    } catch (e: Exception) {
        null
    }
}
