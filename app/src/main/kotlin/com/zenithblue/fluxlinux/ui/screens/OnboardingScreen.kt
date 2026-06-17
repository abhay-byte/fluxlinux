package com.zenithblue.fluxlinux.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalConfiguration
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
import com.zenithblue.fluxlinux.R
import android.content.res.Configuration

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
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
        )

        if (isLandscape) {
            LandscapeLayout(hazeState = hazeState, onGetStarted = onGetStarted)
        } else {
            PortraitLayout(hazeState = hazeState, onGetStarted = onGetStarted)
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun PortraitLayout(
    hazeState: HazeState,
    onGetStarted: () -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_bg_1),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            contentScale = ContentScale.FillWidth
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(60.dp))

                Text(
                    text = "FluxLinux",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Run full Linux desktop environments on your Android device",
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                OnboardingFeatureCards(hazeState = hazeState)

                Spacer(modifier = Modifier.height(24.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            OnboardingGetStartedButton(onGetStarted = onGetStarted)
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun LandscapeLayout(
    hazeState: HazeState,
    onGetStarted: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "FluxLinux",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Run full Linux desktop environments on your Android device",
                color = androidx.compose.material3.MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            Image(
                painter = painterResource(id = R.drawable.onboarding_bg_1),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth(0.9f),
                contentScale = ContentScale.Fit
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        androidx.compose.material3.VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            thickness = 1.dp,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            OnboardingFeatureCards(hazeState = hazeState)

            Spacer(modifier = Modifier.height(24.dp))

            OnboardingGetStartedButton(onGetStarted = onGetStarted)
        }
    }
}

@Composable
private fun OnboardingGetStartedButton(onGetStarted: () -> Unit) {
    Button(
        onClick = onGetStarted,
        colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            "Get Started",
            color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
private fun OnboardingFeatureCards(hazeState: HazeState) {
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
