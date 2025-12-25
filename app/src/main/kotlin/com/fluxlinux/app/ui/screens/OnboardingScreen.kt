package com.fluxlinux.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluxlinux.app.R
import com.fluxlinux.app.ui.theme.FluxAccentCyan
import com.fluxlinux.app.ui.theme.GlassWhiteLow
import com.fluxlinux.app.ui.theme.GlassBorder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit
) {
    val hazeState = androidx.compose.runtime.remember { HazeState() }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F0C29),
                        Color(0xFF302B63),
                        Color(0xFF24243E)
                    )
                )
            )
    ) {
        // Background layer for blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))
            
            // Title
            Text(
                text = "FluxLinux",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Run Full Linux Distributions on Android",
                color = Color(0xFFDDDDDD),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Feature Cards
            FeatureCard(
                hazeState = hazeState,
                icon = "🐧",
                title = "Multiple Distros",
                description = "Debian, Ubuntu, Arch and more"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FeatureCard(
                hazeState = hazeState,
                icon = "🖥️",
                title = "Full Desktop Environment",
                description = "XFCE4 with complete GUI support"
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FeatureCard(
                hazeState = hazeState,
                icon = "⚡",
                title = "No Root Required",
                description = "PRoot mode works on any device"
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Onboarding Illustration
            Image(
                painter = painterResource(id = R.drawable.onboarding_1),
                contentDescription = "Onboarding Illustration",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Get Started Button
            Button(
                onClick = onGetStarted,
                colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Get Started",
                    color = Color.Black,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun FeatureCard(
    hazeState: HazeState,
    icon: String,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .hazeChild(
                state = hazeState,
                style = HazeMaterials.thin(
                    containerColor = GlassWhiteLow
                )
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 32.sp,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = Color(0xFFDDDDDD),
                fontSize = 14.sp
            )
        }
    }
}
