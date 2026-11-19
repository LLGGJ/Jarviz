package com.jarvis.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private val VCyan = Color(0xFF5AA9FF)
private val VMuted = Color(0xFF8A97B3)
private val VPanel = Color(0xFF151C2D)
private const val PPS = 36f   // dp por segundo na timeline

private fun sec(ms: Long): String = String.format(Locale.US, "%.1fs", ms / 1000.0)

private fun loadThumbs(c: Context, cl: VClip, n: Int): List<ImageBitmap> {
    val out = ArrayList<ImageBitmap>()
    try {
        if (cl.media.kind == "image") {
            val opt = BitmapFactory.Options().apply { inSampleSize = 8 }
            val bmp = c.contentResolver.openInputStream(cl.media.uri)?.use { BitmapFactory.decodeStream(it, null, opt) }
            if (bmp != null) repeat(n) { out.add(bmp.asImageBitmap()) }
        } else {
            val r = MediaMetadataRetriever()
            r.setDataSource(c, cl.media.uri)
            for (k in 0 until n) {
                val t = (cl.startMs + (cl.endMs - cl.startMs) * (k + 0.5) / n).toLong() * 1000L
                val f = r.getFrameAtTime(t, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: continue
                out.add(Bitmap.createScaledBitmap(f, 120, 68, true).asImageBitmap())
            }
            r.release()
        }
    } catch (e: Exception) { }
    return out
}

/** Editor de vídeo: prévia, timeline com miniaturas, linha de legendas, trilha de música com onda e agulha que anda. Tudo se mexe ao vivo quando o Jarvis edita. */
@Composable
fun VideoEditorScreen(vm: JarvisViewModel) {
    val ctx = LocalContext.current
    BackHandler(enabled = true) { VideoEditor.open = false }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { VideoEditor.addMedia(ctx, it) }
    val player = remember { ExoPlayer.Builder(ctx).build() }
    var posMs by remember { mutableStateOf(0L) }
    var showFinal by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val l = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val cl = VideoEditor.clips.filter { it.media.kind == "video" }.getOrNull(player.currentMediaItemIndex)
                player.setPlaybackSpeed(if (showFinal) 1f else (cl?.speed ?: 1f))
            }
        }
        player.addListener(l)
        onDispose { player.removeListener(l); player.release() }
    }
    val key = VideoEditor.clips.joinToString("|") { "${it.media.id}:${it.startMs}:${it.endMs}" }
    val finalUri = VideoEditor.exportedUri
    LaunchedEffect(key, showFinal, finalUri) {
        if (showFinal && finalUri != null) {
            player.setMediaItem(MediaItem.fromUri(finalUri))
            player.setPlaybackSpeed(1f)
        } else {
            player.setMediaItems(VideoEditor.clips.filter { it.media.kind == "video" }.map { c ->
                MediaItem.Builder().setUri(c.media.uri).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder().setStartPositionMs(c.startMs).setEndPositionMs(c.endMs).build()
                ).build()
            })
        }
        player.prepare()
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(80)
            if (!showFinal && player.isPlaying) {
                val vids = VideoEditor.clips.withIndex().filter { it.value.media.kind == "video" }
                val cur = vids.getOrNull(player.currentMediaItemIndex)
                if (cur != null) posMs = VideoEditor.clips.take(cur.index).sumOf { it.lenMs } + (player.currentPosition / cur.value.speed).toLong()
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0E1424), Color(0xFF182036))))
            .statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).clickable { VideoEditor.open = false }, contentAlignment = Alignment.Center) {
                NeonIcon(Ico.CLOSE, Modifier.size(18.dp), VMuted)
            }
            Spacer(Modifier.weight(1f))
            if (finalUri != null) {
                Box(Modifier.clip(RoundedCornerShape(12.dp)).background(if (showFinal) VCyan.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                    .clickable { showFinal = !showFinal }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (showFinal) "Voltar à edição" else "Ver o resultado", color = Color.White, fontSize = 12.sp)
                }
                Spacer(Modifier.width(8.dp))
            }
            Box(Modifier.size(80.dp)) {
                RobotFace(vm.shown, { vm.level }, if (vm.state == State.THINKING) "work" else "", false, Modifier.fillMaxSize(),
                    onTap = { if (vm.state == State.THINKING) vm.cancel() else { BeepGuard.pre(); vm.listenNow() } }, mood = Mood.current)
            }
        }

        // prévia
        Box(Modifier.weight(1.15f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black)) {
            val hasVideo = VideoEditor.clips.any { it.media.kind == "video" }
            if (hasVideo || (showFinal && finalUri != null)) {
                AndroidView(factory = { c -> val pl = player; PlayerView(c).apply { this.player = pl; useController = true } }, modifier = Modifier.fillMaxSize())
            } else {
                val sel = VideoEditor.clips.getOrNull(VideoEditor.selected) ?: VideoEditor.clips.firstOrNull()
                val img by produceState<List<ImageBitmap>>(emptyList(), sel?.media?.id, sel?.startMs) {
                    value = if (sel == null) emptyList() else withContext(Dispatchers.IO) { loadThumbs(ctx, sel, 1) }
                }
                if (img.isNotEmpty()) Image(img[0], null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else Text("A prévia aparece aqui.\nToque em + Mídia e peça a edição ao Jarvis.", color = VMuted, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center).padding(24.dp))
            }
            // o que o Jarvis está fazendo agora
            if (VideoEditor.lastAction.isNotBlank() || VideoEditor.exporting) {
                val pulse by rememberInfiniteTransition(label = "p").animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pp")
                Row(Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(VCyan.copy(alpha = pulse)))
                    Text(if (VideoEditor.exporting) VideoEditor.status.ifBlank { "exportando" } else "Jarvis: " + VideoEditor.lastAction, color = Color.White, fontSize = 12.sp)
                }
            }
            val selClip = VideoEditor.clips.getOrNull(VideoEditor.selected)
            if (!showFinal && selClip != null && (selClip.text.isNotBlank() || selClip.filter.isNotBlank() || selClip.transition.isNotBlank())) {
                Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (selClip.text.isNotBlank()) Text(selClip.text, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 10.dp, vertical = 4.dp))
                    val fx = listOf(selClip.filter, selClip.transition).filter { it.isNotBlank() }.joinToString(" · ")
                    if (fx.isNotBlank()) Text("efeitos: $fx (aparecem no vídeo final)", color = Color(0xFFFFE08A), fontSize = 10.sp)
                }
            }
        }

        Timeline(posMs, Modifier.weight(1f).fillMaxWidth().padding(12.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("+ Mídia", pad = 12.dp, icon = Ico.CLIP) { picker.launch(arrayOf("video/*", "image/*", "audio/*")) }
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VideoEditor.media.forEach { m ->
                    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(VPanel).padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text("${m.id} · ${m.name}", color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(110.dp))
                    }
                }
            }
            NeonButton("Exportar", active = VideoEditor.exporting, pad = 12.dp, icon = Ico.DOWNLOAD) { VideoEditor.status = VideoEditor.export("vertical") }
        }
        if (VideoEditor.status.isNotBlank() && !VideoEditor.exporting) {
            Text(VideoEditor.status, color = VMuted, fontSize = 11.sp, modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp), maxLines = 2)
        }
    }
}

@Composable
private fun Timeline(posMs: Long, modifier: Modifier) {
    val ctx = LocalContext.current
    val clips = VideoEditor.clips
    val totalMs = clips.sumOf { it.lenMs }
    val totalSec = (totalMs / 1000f)
    val widthDp = maxOf((totalSec * PPS + 60f).dp, 300.dp)
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(VPanel)) {
        Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            Column(Modifier.width(widthDp).padding(vertical = 8.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // régua de tempo
                Box(Modifier.fillMaxWidth().height(16.dp)) {
                    for (s in 0..totalSec.toInt() step 2) {
                        Text("${s}s", color = VMuted, fontSize = 9.sp, modifier = Modifier.offset(x = (s * PPS).dp))
                    }
                }
                // vídeo e fotos
                if (clips.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, VMuted.copy(alpha = 0.4f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                        Text("Timeline vazia", color = VMuted, fontSize = 12.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    clips.forEachIndexed { i, c -> key(i, c) { ClipBlock(i, c) } }
                }
                // legendas
                if (clips.any { it.text.isNotBlank() }) {
                    Box(Modifier.fillMaxWidth().height(22.dp)) {
                        var off = 0L
                        clips.forEach { c ->
                            if (c.text.isNotBlank()) {
                                Box(Modifier.offset(x = (off / 1000f * PPS).dp).width((c.lenMs / 1000f * PPS).dp).height(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF8A6A1F)).padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart) {
                                    Text("T: ${c.text}", color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            off += c.lenMs
                        }
                    }
                }
                // música com onda
                val mus = VideoEditor.music
                if (mus != null) {
                    val wf by produceState(FloatArray(0), mus.id) { value = withContext(Dispatchers.IO) { VideoEditor.waveform(ctx, mus, 160) } }
                    val musSec = if (mus.durMs > 0) minOf(totalSec, mus.durMs / 1000f) else totalSec
                    Box(Modifier.width((musSec * PPS).dp).height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF2A2158))) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (wf.isNotEmpty()) {
                                val bw = size.width / wf.size
                                for (k in wf.indices) {
                                    val hh = (wf[k] * size.height * 0.9f).coerceAtLeast(2f)
                                    drawLine(Color(0xFFB5A6FF), Offset(k * bw + bw / 2f, size.height / 2f - hh / 2f), Offset(k * bw + bw / 2f, size.height / 2f + hh / 2f), bw * 0.6f)
                                }
                            }
                        }
                        Text(mus.name, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 6.dp, top = 2.dp))
                    }
                }
            }
            // agulha
            Box(Modifier.offset(x = (posMs / 1000f * PPS + 6f).dp).width(2.dp).fillMaxHeight().background(Color(0xFFFF5A5A)))
        }
    }
}

@Composable
private fun ClipBlock(i: Int, c: VClip) {
    val ctx = LocalContext.current
    val wDp = (c.lenMs / 1000f * PPS).dp.coerceAtLeast(40.dp)
    val n = (wDp.value / 54f).toInt().coerceIn(1, 12)
    val thumbs by produceState<List<ImageBitmap>>(emptyList(), c.media.id, c.startMs, c.endMs, n) {
        value = withContext(Dispatchers.IO) { loadThumbs(ctx, c, n) }
    }
    val sel = VideoEditor.selected == i
    val vis = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(vis, enter = fadeIn() + scaleIn(initialScale = 0.6f) + expandHorizontally()) {
        Box(
            Modifier.width(wDp).height(56.dp).clip(RoundedCornerShape(8.dp))
                .border(if (sel) 2.dp else 1.dp, if (sel) VCyan else Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                .clickable { VideoEditor.selected = i }
        ) {
            if (thumbs.isEmpty()) Box(Modifier.fillMaxSize().background(if (c.media.kind == "image") Color(0xFF1F7A5C) else Color(0xFF2B4A7A)))
            else Row(Modifier.fillMaxSize()) { thumbs.forEach { Image(it, null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop) } }
            Text(
                "${i + 1} · " + sec(c.lenMs) + (if (c.speed != 1f) " x${c.speed}" else ""),
                color = Color.White, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomStart).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}
