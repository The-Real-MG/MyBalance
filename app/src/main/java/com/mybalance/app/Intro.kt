package com.mybalance.app

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Nome dello sviluppatore (uguale al nome del profilo su Google Play). */
const val DEVELOPER_NAME = "TheRealMG"

/** Intro a schermo nero: "An app by" piccolo sopra, nome grande in dissolvenza. Tocca per saltare. */
@Composable
fun IntroSplash(onDone: () -> Unit) {
    val small = remember { Animatable(0f) }
    val big = remember { Animatable(0f) }
    val all = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        delay(250)
        small.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        big.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
        delay(900)
        all.animateTo(0f, tween(500))
        onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDone
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { alpha = all.value }
        ) {
            Text(
                "An app by",
                color = Color.White,
                fontSize = 14.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.graphicsLayer { alpha = small.value * 0.75f }
            )
            Spacer(Modifier.height(6.dp))
            Text(
                DEVELOPER_NAME,
                color = Color.White,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.graphicsLayer {
                    alpha = big.value
                    val s = 0.92f + 0.08f * big.value
                    scaleX = s
                    scaleY = s
                }
            )
        }
    }
}
