package com.jarvis.app

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.util.Locale

private val VCyan = Color(0xFF5AA9FF)
private val VMuted = Color(0xFF8A97B3)
private val VPanel = Color(0xFF1B2438)

private fun sec(ms: Long): String = String.format(Locale.US, "%.1fs", ms / 1000.0)

/** Tela do editor: prévia, timeline que se monta ao vivo, bandeja de mídias e exportação. O robozinho fica no canto para você falar com ele. */
@Composable
fun VideoEditorScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    BackHandler(enabled = true) { VideoEditor.open = false }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { VideoEditor.addMedia(ctx, it) }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(Unit) { onDispose { player.release() } }
    val previewKey = VideoEditor.clips.joinToString("|") { "${it.media.id}:${it.startMs}:${it.endMs}" }
    LaunchedEffect(previewKey) {
        val items = VideoEditor.clips.filter { it.media.kind == "video" }.map { c ->
            MediaItem.Builder().setUri(c.media.uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
            ).build()
        }
        player.setMediaItems(items)
        player.prepare()
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF121826), Color(0xFF182036))))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            NeonButton("Voltar", pad = 12.dp, icon = Ico.CLOSE) { VideoEditor.open = false }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("EDITOR DE VÍDEO", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Toque no robô e diga o que quer", color = VMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(86.dp)) {
                RobotFace(vm.shown, { vm.level }, if (vm.state == State.THINKING) "work" else "", false, Modifier.fillMaxSize(),
                    onTap = { if (vm.state == State.THINKING) vm.cancel() else vm.listenNow() })
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            if (VideoEditor.clips.none { it.media.kind == "video" }) {
                Text("A prévia aparece aqui quando houver vídeos na timeline.", color = VMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            } else {
                AndroidView(factory = { c ->
                    val pl = player
                    PlayerView(c).apply { this.player = pl; useController = true }
                }, modifier = Modifier.fillMaxSize())
            }
        }

        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val total = VideoEditor.clips.sumOf { it.lenMs }
            Text("TIMELINE · ${VideoEditor.clips.size} clipes · ${sec(total)}", color = VMuted, fontSize = 11.sp, letterSpacing = 1.sp)
            Row(Modifier.fillMaxWidth().height(76.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (VideoEditor.clips.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, VMuted.copy(alpha = 0.4f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Text("Vazia. Toque em + Mídia e peça a edição ao Jarvis.", color = VMuted, fontSize = 12.sp)
                    }
                }
                VideoEditor.clips.forEachIndexed { i, c ->
                    key(i, c) {
                        val vis = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(vis, enter = fadeIn() + scaleIn() + expandHorizontally()) {
                            val sel = VideoEditor.selected == i
                            val base = if (c.media.kind == "image") Color(0xFF1F7A5C) else Color(0xFF2B4A7A)
                            Column(
                                Modifier.width((c.lenMs / 1000f * 22f).dp.coerceIn(84.dp, 360.dp)).height(76.dp).clip(RoundedCornerShape(12.dp))
                                    .background(base).border(if (sel) 2.dp else 1.dp, if (sel) VCyan else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    .clickable { VideoEditor.selected = i }.padding(8.dp),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${i + 1} · ${c.media.name}", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(sec(c.lenMs) + if (c.speed != 1f) " · x${c.speed}" else "", color = Color.White.copy(alpha = 0.8f), fontSize = 10.sp)
                                if (c.text.isNotBlank()) Text("T: ${c.text}", color = Color(0xFFFFE08A), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            val mus = VideoEditor.music
            if (mus != null) {
                Box(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF4B3A8A)).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
                    Text("Música: ${mus.name}", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                NeonButton("+ Mídia", pad = 12.dp, icon = Ico.CLIP) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
                VideoEditor.media.forEach { m ->
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(VPanel).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("${m.id} · ${m.name}", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(120.dp))
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Exportar vídeo", active = VideoEditor.exporting, pad = 12.dp, icon = Ico.DOWNLOAD) {
                    VideoEditor.status = VideoEditor.export("vertical")
                }
                Text(VideoEditor.status, color = VMuted, fontSize = 11.sp, maxLines = 3, modifier = Modifier.weight(1f))
            }
        }
    }
}
