package com.deivid22srk.dkc2recomp.launcher.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Chip técnico do canto inferior esquerdo — o "canto de credibilidade" típico
 * de menus AAA. Cada token é um fato verificável deste projeto, sem marketing:
 *
 *  - SDL2     : o motor do jogo é o host SDL estático (CMake FetchContent
 *               pin release-2.30.9); é ele quem roda o loop de gameplay.
 *  - GLES2    : o apresentador cria um contexto OpenGL ES 2.0 explícito
 *               (runner/desktop_present_sdl.c — SDL_GL_CONTEXT_MAJOR_VERSION 2
 *               + SDL_GL_CONTEXT_PROFILE_ES). Não exibimos a versão máxima
 *               que o APARELHO suporta (glEsVersion do device), porque o jogo
 *               não roda nela: roda no contexto ES2 solicitado.
 *  - ABI      : detectada em runtime de Build.SUPPORTED_ABIS — e o APK só
 *               contém arm64-v8a (abiFilters no build.gradle.kts).
 *
 * A interface desta tela é Compose, mas isso é instrumento de UI, não o
 * motor do jogo — por isso não entra no chip.
 */
@Composable
fun TechStatusChip(modifier: Modifier = Modifier) {
    val info = remember {
        val abi = Build.SUPPORTED_ABIS.firstOrNull()?.uppercase() ?: "ARM64-V8A"
        listOf("SDL2", "GLES2", abi).joinToString("  ·  ")
    }

    Box(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = info,
            color = Color.White.copy(alpha = 0.50f),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp
        )
    }
}
