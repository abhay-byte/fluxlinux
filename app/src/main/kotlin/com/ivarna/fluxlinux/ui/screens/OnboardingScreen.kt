package com.ivarna.fluxlinux.ui.screens

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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivarna.fluxlinux.R
import com.ivarna.fluxlinux.ui.theme.FluxAccentCyan
import com.ivarna.fluxlinux.ui.theme.GlassWhiteLow
import com.ivarna.fluxlinux.ui.theme.GlassBorder
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.HazeStyle

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun OnboardingScreen(
    onGetStarted: () -> Unit
) {
    val hazeState = androidx.compose.runtime.remember { HazeState() }
    var currentStep by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        // Background layer for blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )

        // Footer Image (Step 1 only)
        if (currentStep == 0) {
             Image(
                painter = painterResource(id = R.drawable.onboarding_bg_1),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
        }
        
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
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subtitle
            Text(
                text = "Run Full Linux Distributions on Android",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha=0.7f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            androidx.compose.animation.AnimatedContent(targetState = currentStep, label = "onboarding_step") { step ->
                if (step == 0) {
                    // STEP 1: Intro
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Continue Button
                        Button(
                            onClick = { currentStep = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = FluxAccentCyan),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Next",
                                color = Color.Black,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // STEP 2: Permissions
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                         // Permission Info Card
                         androidx.compose.material3.Card(
                            colors = androidx.compose.material3.CardDefaults.cardColors(
                                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
                         ) {
                             Column(modifier = Modifier.padding(20.dp)) {
                                 Text(
                                    "⚠️ Critical Permission",
                                    color = Color(0xFFFFB74D), // Warning Orange
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                 )
                                 Spacer(modifier = Modifier.height(8.dp))
                                 Text(
                                    "To display the Linux desktop (X11) on your screen, Termux needs the 'Display over other apps' permission.",
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                 )
                             }
                         }
                         
                         Spacer(modifier = Modifier.height(24.dp))
                         
                         // Action Buttons
                         Row(
                             modifier = Modifier.fillMaxWidth(),
                             horizontalArrangement = Arrangement.spacedBy(12.dp)
                         ) {
                             // Overlay Settings Button
                             Button(
                                 onClick = { 
                                     try {
                                         val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                         intent.data = android.net.Uri.parse("package:com.termux")
                                         context.startActivity(intent)
                                     } catch (e: Exception) {
                                          try {
                                             val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                                             context.startActivity(intent)
                                         } catch (e2: Exception) {
                                             android.widget.Toast.makeText(context, "Could not open settings", android.widget.Toast.LENGTH_SHORT).show()
                                         }
                                     }
                                 },
                                 colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary),
                                 modifier = Modifier.weight(1f),
                                 shape = RoundedCornerShape(12.dp)
                             ) {
                                 Text("Enable Overlay", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary, textAlign = TextAlign.Center, lineHeight = 16.sp)
                             }
                             
                             // App Info Button
                             Button(
                                 onClick = { 
                                     try {
                                         val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                         intent.data = android.net.Uri.parse("package:com.termux")
                                         context.startActivity(intent)
                                     } catch (e: Exception) {
                                         android.widget.Toast.makeText(context, "Could not open App Info", android.widget.Toast.LENGTH_SHORT).show()
                                     }
                                 },
                                 colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondary),
                                 modifier = Modifier.weight(1f),
                                 shape = RoundedCornerShape(12.dp)
                             ) {
                                 Text("App Info", fontSize = 13.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSecondary, textAlign = TextAlign.Center, lineHeight = 16.sp)
                             }
                         }
                         
                         Spacer(modifier = Modifier.height(16.dp))

                         // Help Link
                         androidx.compose.material3.TextButton(
                             onClick = { 
                                 val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://support.google.com/android/answer/12623953?hl=en"))
                                 context.startActivity(browserIntent)
                             }
                         ) {
                             Text(
                                 "How to allow restricted settings on Android devices",
                                 color = FluxAccentCyan,
                                 fontSize = 13.sp,
                                 textAlign = TextAlign.Center,
                                 textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                             )
                         }
                         
                         Spacer(modifier = Modifier.height(8.dp))
                         
                         // Mandatory Checkbox
                         var isChecked by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                         
                         Row(
                             verticalAlignment = Alignment.CenterVertically,
                             modifier = Modifier
                                 .fillMaxWidth()
                                 .clickable { isChecked = !isChecked }
                                 .padding(8.dp)
                         ) {
                             androidx.compose.material3.Checkbox(
                                 checked = isChecked,
                                 onCheckedChange = { isChecked = it },
                                 colors = androidx.compose.material3.CheckboxDefaults.colors(
                                     checkedColor = FluxAccentCyan,
                                     uncheckedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha=0.6f)
                                 )
                             )
                             Text(
                                 "I have enabled 'Display over other apps' for Termux",
                                 fontSize = 14.sp,
                                 color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                                 modifier = Modifier.padding(start = 8.dp)
                             )
                         }
                         
                         Spacer(modifier = Modifier.height(32.dp))
                         
                         // Next Button
                         Button(
                            onClick = onGetStarted,
                            enabled = isChecked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isChecked) FluxAccentCyan else androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isChecked) Color.Black else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                "Next",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Only show image on first step or keep small? Removing large image to fit new content or keeping?
            // User request implies focusing on the permission. I will keep the image only in step 0 if it fits, or remove it from the main flow to avoid clutter. 
            // The original code had the image at the bottom.
            // Let's hide the image for step 1 to save space for permission info
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
                style = HazeStyle(
                    backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    tint = null
                )
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Text(
            text = icon,
            fontSize = 32.sp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(end = 16.dp)
        )
        
        Column {
            Text(
                text = title,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontSize = 14.sp
            )
        }
    }
}
