package com.ivarna.fluxlinux.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.ui.theme.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootAccessScreen(
    onBack: () -> Unit,
    onEnableChroot: () -> Unit
) {
    var isRooted by remember { mutableStateOf(false) }
    var checkComplete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isRooted = checkForRoot()
        checkComplete = true
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Root Access", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                ),
                modifier = Modifier.background(Color.Transparent)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(Color(0xFF0F0C29), Color(0xFF24243E))
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // Icon
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(GlassWhiteLow),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (checkComplete && isRooted) Icons.Default.Lock else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (checkComplete && isRooted) Color(0xFF50fa7b) else Color(0xFFFF6B6B),
                        modifier = Modifier.size(64.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (!checkComplete) {
                    CircularProgressIndicator(color = FluxAccentCyan)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Checking for root access...", color = Color.Gray)
                } else {
                    if (isRooted) {
                        Text(
                            "Root Access Detected",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF50fa7b)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your device is rooted. You can enable Chroot mode for better performance and full hardware access.",
                            textAlign = TextAlign.Center,
                            color = Color(0xFFDDDDDD)
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onEnableChroot,
                            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Enable Chroot Mode", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "No Root Access",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF6B6B)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Your device does not appear to be rooted. Chroot mode requires root access to function.",
                            textAlign = TextAlign.Center,
                            color = Color(0xFFDDDDDD)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Please continue using PRoot mode (Default). It works on all devices without root.",
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(40.dp))
                        Button(
                            onClick = onBack,
                            colors = ButtonDefaults.buttonColors(containerColor = GlassBorder),
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Return to Settings", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

private fun checkForRoot(): Boolean {
    val paths = arrayOf(
        "/system/app/Superuser.apk",
        "/sbin/su",
        "/system/bin/su",
        "/system/xbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su"
    )
    return try {
        paths.any { File(it).exists() }
    } catch (e: Exception) {
        false
    }
}
