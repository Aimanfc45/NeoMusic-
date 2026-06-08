package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.delay
import kotlin.random.Random

// High fidelity UI Track model
data class Track(
    val id: Int,
    val title: String,
    val artist: String,
    val duration: String,
    val durationSec: Int,
    val accentColor: Color,
    val audioUrl: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                NeoMusicApp()
            }
        }
    }
}

// Keep a delegate Greeting composable so existing tests compile successfully
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        NeoMusicApp()
    }
}

// Custom Premium Colors
val DeepBlack = Color(0xFF030206)
val CardSurface = Color(0xFF0D0B12)
val NeonPurple = Color(0xFFBD00FF)
val NeonOrchid = Color(0xFFFF00D6)
val NeonGold = Color(0xFFFFB703)
val BrightGold = Color(0xFFFFD700)
val CyberCyan = Color(0xFF00E5FF)
val DullGrey = Color(0xFF7E7C84)
val LightGrey = Color(0xFFE2DFE8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeoMusicApp() {
    val context = LocalContext.current
    
    // Core App State
    var coinCount by remember { mutableStateOf(245) }
    var dailyStreak by remember { mutableStateOf(6) }
    var checkedInToday by remember { mutableStateOf(false) }
    
    // Media Player State
    var isPlaying by remember { mutableStateOf(false) }
    var currentTrackIndex by remember { mutableStateOf(0) }
    var currentSongProgress by remember { mutableStateOf(34) } // Starts at 34s for feedback
    var totalSecondsListened by remember { mutableStateOf(0) }
    
    // Bottom Tab Selector (0: Utama/Muzik, 1: Tugasan Harian, 2: Dompet Krypto, 3: Profil)
    var selectedTab by remember { mutableStateOf(0) }
    
    // Tasks claimed statuses
    var codeClaimed by remember { mutableStateOf(false) }
    var listeningRewardClaimed by remember { mutableStateOf(false) }
    var codeInput by remember { mutableStateOf("") }
    
    // Settings state
    var neonGlowEnabled by remember { mutableStateOf(true) }
    var highFiEnabled by remember { mutableStateOf(true) }
    var autoPlayNext by remember { mutableStateOf(true) }
    
    // Neon glow borders Modifier Helper
    val neonBorderBrush = Brush.linearGradient(
        colors = if (neonGlowEnabled) listOf(NeonPurple, NeonOrchid) else listOf(DullGrey, CardSurface)
    )

    // Tracks List with stable public MP3/HLS Streaming URLs for Fasa 2.9 (Radio Syok & Live)
    val tracksList = remember {
        listOf(
            Track(1, "ERA FM (Muzik Melayu)", "Syok Radio Live", "LIVE", 0, Color(0xFFFF00D6), "https://playerservices.streamtheworld.com/api/livestream-redirect/ERAFM.mp3"),
            Track(2, "SINAR FM (Nusantara Klasik)", "Syok Sinar Klasik", "LIVE", 0, Color(0xFFBD00FF), "https://playerservices.streamtheworld.com/api/livestream-redirect/SINARFM.mp3"),
            Track(3, "HITZ FM (English Pop)", "Syok Top 40 Hits", "LIVE", 0, Color(0xFF00E5FF), "https://playerservices.streamtheworld.com/api/livestream-redirect/HITZFM.mp3"),
            Track(4, "MIX FM (Classic Rock/Pop)", "Syok Classic Retro", "LIVE", 0, Color(0xFFFFB703), "https://playerservices.streamtheworld.com/api/livestream-redirect/MIXFM.mp3")
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            tracksList
        } else {
            tracksList.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    
    val currentTrack = tracksList[currentTrackIndex]

    // Configure HttpDataSource.Factory with user-agent representation
    val httpDataSourceFactory = remember {
        androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/95.0.4638.74 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
    }

    // Configure standard dynamic MediaSource.Factory to auto-detect both progressive MP3s and HLS penstriman langsung
    val mediaSourceFactory = remember {
        androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
            .setDataSourceFactory(httpDataSourceFactory)
    }

    // Initialize ExoPlayer with the customized mediaSourceFactory
    val exoPlayer = remember {
        androidx.media3.exoplayer.ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    // Set ExoPlayer list playback state listener, also syncing isPlaying from source-of-truth onIsPlayingChanged
    DisposableEffect(exoPlayer) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    if (autoPlayNext) {
                        currentTrackIndex = (currentTrackIndex + 1) % tracksList.size
                        currentSongProgress = 0
                    } else {
                        isPlaying = false
                        currentSongProgress = 0
                    }
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    // Ensure safe release of ExoPlayer
    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    // Prepare and play media source when track changes
    LaunchedEffect(currentTrackIndex) {
        val track = tracksList[currentTrackIndex]
        
        // Let ExoPlayer handle type discovery or fall back to MimeTypes detection
        val mimeType = if (track.audioUrl.contains(".m3u8")) {
            androidx.media3.common.MimeTypes.APPLICATION_M3U8
        } else if (track.audioUrl.contains(".aac")) {
            androidx.media3.common.MimeTypes.AUDIO_AAC
        } else {
            androidx.media3.common.MimeTypes.AUDIO_MPEG
        }

        val mediaItem = androidx.media3.common.MediaItem.Builder()
            .setUri(track.audioUrl)
            .setMimeType(mimeType)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        if (isPlaying) {
            exoPlayer.play()
        } else {
            if (track.durationSec > 0) {
                exoPlayer.seekTo(currentSongProgress.toLong() * 1000L)
            }
        }
    }

    // Listen state updates from Compose variable
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
        } else {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            }
        }
    }

    // Sync Slider/Progress animation from current active ExoPlayer position or increment locally
    LaunchedEffect(isPlaying, currentTrackIndex) {
        if (isPlaying) {
            while (isPlaying) {
                val posSeconds = (exoPlayer.currentPosition / 1000).toInt()
                currentSongProgress = if (posSeconds > 0) posSeconds else (currentSongProgress + 1)
                delay(500L)
            }
        }
    }

    // Coins Accumulator listener (+10 Coins every 1 minute of aggregate play time)
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(1000L)
                totalSecondsListened++
                if (totalSecondsListened > 0 && totalSecondsListened % 60 == 0) {
                    coinCount += 10
                    Toast.makeText(context, "Tahniah! +10 Koin diperoleh kerana mendengar selama 1 minit!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("neomusic_root"),
        containerColor = DeepBlack,
        bottomBar = {
            // 3. Bottom Navigation Bar Component
            NavigationBar(
                containerColor = CardSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        BorderStroke(0.5.dp, Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color.Transparent))),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                tonalElevation = 8.dp
            ) {
                val menuItems = listOf(
                    Triple("Muzik", Icons.Default.PlayArrow, 0),
                    Triple("Tugasan", Icons.Default.Check, 1),
                    Triple("Dompet", Icons.Default.Star, 2), // Representing wallet/rewards
                    Triple("Profil", Icons.Default.Person, 3)
                )

                menuItems.forEach { (label, icon, index) ->
                    val isSelected = selectedTab == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) NeonPurple else DullGrey
                            )
                        },
                        label = {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = if (isSelected) LightGrey else DullGrey,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = NeonPurple.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(DeepBlack)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            
            // 1. Bahagian Atas (Header Card)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, neonBorderBrush), RoundedCornerShape(16.dp))
                    .shadow(
                        elevation = if (neonGlowEnabled) 8.dp else 0.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = NeonPurple,
                        spotColor = NeonPurple
                    )
                    .testTag("app_header_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Application Title
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "NEOMUSIC",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                style = TextStyle(
                                    brush = Brush.horizontalGradient(listOf(NeonOrchid, NeonPurple, CyberCyan))
                                ),
                                modifier = Modifier.testTag("app_name_title")
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "v1.0.2",
                                    fontSize = 10.sp,
                                    color = NeonGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Play & Earn Ecosystem",
                            fontSize = 11.sp,
                            color = DullGrey,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Koin Aktif Card + Daily Streak Counter
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Koin Aktif
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(BorderStroke(0.5.dp, BrightGold.copy(alpha = 0.5f)), RoundedCornerShape(24.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            NeoCoinIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "$coinCount",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = BrightGold,
                                modifier = Modifier.testTag("coin_text")
                            )
                        }

                        // Streak Indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.05f))
                                .border(BorderStroke(0.5.dp, Color(0xFFFF5500).copy(alpha = 0.5f)), RoundedCornerShape(24.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            NeoFlameIcon(size = 18.dp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${dailyStreak}D",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFF7700),
                                modifier = Modifier.testTag("streak_text")
                            )
                        }
                    }
                }
            }

            // Tabs Content (Change Center Components dynamically)
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "TabTransition"
            ) { targetTab ->
                when (targetTab) {
                    0 -> {
                        // === TAB 1: UTAMA / MUZIK PAGE ===
                        Column(
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 2. Bahagian Tengah (Main Player View)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, neonBorderBrush), RoundedCornerShape(24.dp))
                                    .testTag("main_player_box"),
                                shape = RoundedCornerShape(24.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Visualizer & Spinning Record representation
                                    Box(
                                        modifier = Modifier
                                            .size(160.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF07050A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Rotating vinyl visual
                                        VinylSpindleAnimation(
                                            isPlaying = isPlaying,
                                            accentColor = currentTrack.accentColor
                                        )
                                        
                                        // Static Center Badge
                                        Box(
                                            modifier = Modifier
                                                .size(50.dp)
                                                .clip(CircleShape)
                                                .background(DeepBlack)
                                                .border(BorderStroke(1.dp, currentTrack.accentColor), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Spindle",
                                                tint = currentTrack.accentColor.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Dynamic spectrum visualizer
                                    NeonSpectrumVisualizer(isPlaying = isPlaying, tint = currentTrack.accentColor)

                                    // Song Detail Metadata
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = currentTrack.title,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.testTag("song_title_text")
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = currentTrack.artist,
                                            fontSize = 14.sp,
                                            color = currentTrack.accentColor,
                                            textAlign = TextAlign.Center,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.testTag("song_artist_text")
                                        )
                                    }

                                    // Progress timeline / seek bar
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Seek Bar (Slider)
                                        val maxProgress = if (currentTrack.durationSec > 0) currentTrack.durationSec.toFloat() else maxOf(3600f, currentSongProgress.toFloat() * 1.5f)
                                        Slider(
                                            value = minOf(currentSongProgress.toFloat(), maxProgress),
                                            onValueChange = { newValue ->
                                                currentSongProgress = newValue.toInt()
                                                if (currentTrack.durationSec > 0) {
                                                    exoPlayer.seekTo(newValue.toLong() * 1000L)
                                                }
                                            },
                                            valueRange = 0f..maxProgress,
                                            colors = SliderDefaults.colors(
                                                thumbColor = currentTrack.accentColor,
                                                activeTrackColor = currentTrack.accentColor,
                                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("song_seek_bar")
                                        )

                                        // Time indicators
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatTime(currentSongProgress),
                                                color = DullGrey,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = if (currentTrack.durationSec > 0) currentTrack.duration else "LIVE",
                                                color = if (currentTrack.durationSec > 0) DullGrey else currentTrack.accentColor,
                                                fontWeight = if (currentTrack.durationSec > 0) FontWeight.Normal else FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    // Player Controls Controls
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Previous track button
                                        IconButton(
                                            onClick = {
                                                currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else tracksList.size - 1
                                                currentSongProgress = 0
                                            },
                                            modifier = Modifier.size(48.dp).testTag("rewind_button")
                                        ) {
                                            NeoRewindIcon(size = 24.dp, tint = LightGrey)
                                        }

                                        // Play / Pause Circle
                                        Box(
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    Brush.radialGradient(
                                                        listOf(currentTrack.accentColor, currentTrack.accentColor.copy(alpha = 0.7f))
                                                    )
                                                )
                                                .clickable { isPlaying = !isPlaying }
                                                .testTag("play_pause_button"),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isPlaying) {
                                                NeoPauseIcon(size = 28.dp, tint = DeepBlack)
                                            } else {
                                                NeoPlayIcon(size = 28.dp, tint = DeepBlack)
                                            }
                                        }

                                        // Next track button
                                        IconButton(
                                            onClick = {
                                                currentTrackIndex = (currentTrackIndex + 1) % tracksList.size
                                                currentSongProgress = 0
                                            },
                                            modifier = Modifier.size(48.dp).testTag("skip_button")
                                        ) {
                                            NeoSkipIcon(size = 24.dp, tint = LightGrey)
                                        }
                                    }
                                }
                            }

                            // Tracks List Section (Daftar Cadangan Pilihan)
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Senarai Pilihan",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LightGrey,
                                    modifier = Modifier.padding(start = 4.dp)
                                )

                                // Search Bar Component (Kotak Carian Premium)
                                TextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            BorderStroke(1.dp, NeonPurple.copy(alpha = 0.3f)),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .testTag("track_search_bar"),
                                    placeholder = {
                                        Text(
                                            text = "Cari lagu atau artis dunia...",
                                            color = DullGrey,
                                            fontSize = 14.sp
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = "Search Icon",
                                            tint = NeonOrchid
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Clear Search",
                                                    tint = DullGrey
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color(0xFF1D0F35),
                                        unfocusedContainerColor = Color(0xFF110822),
                                        disabledContainerColor = Color(0xFF110822),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        cursorColor = NeonPurple,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    )
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                if (filteredTracks.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Tiada lagu ditemui untuk \"$searchQuery\"",
                                            color = DullGrey,
                                            fontSize = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    filteredTracks.forEach { track ->
                                        val realIndex = tracksList.indexOf(track)
                                        val isCurrent = currentTrackIndex == realIndex
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isCurrent) NeonPurple.copy(alpha = 0.15f) else CardSurface)
                                                .clickable {
                                                    if (realIndex != -1) {
                                                        currentTrackIndex = realIndex
                                                        currentSongProgress = 0
                                                        isPlaying = true
                                                    }
                                                }
                                                .border(
                                                    BorderStroke(
                                                        0.5.dp,
                                                        if (isCurrent) NeonPurple else Color.White.copy(alpha = 0.05f)
                                                    ),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(12.dp)
                                                .testTag("track_item_$realIndex"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // Visual thumbnail
                                            Box(
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(track.accentColor.copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Canvas(modifier = Modifier.size(16.dp)) {
                                                    drawRoundRect(
                                                        color = track.accentColor,
                                                        cornerRadius = CornerRadius(2.dp.toPx())
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            // Meta column
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = track.title,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrent) NeonPurple else Color.White
                                                )
                                                Text(
                                                    text = track.artist,
                                                    fontSize = 12.sp,
                                                    color = DullGrey
                                                )
                                            }

                                            // Status or timing
                                            if (isCurrent && isPlaying) {
                                                Text(
                                                    text = "SEDANG DIIKUTI",
                                                    fontSize = 10.sp,
                                                    color = NeonPurple,
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.sp
                                                )
                                            } else {
                                                Text(
                                                    text = track.duration,
                                                    fontSize = 12.sp,
                                                    color = DullGrey
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // === TAB 2: TUGASAN HARIAN ===
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("tasks_tab")
                        ) {
                            Text(
                                text = "Misi & Ganjaran Hari Ini",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightGrey
                            )
                            Text(
                                text = "Dengar muzik kegemaran anda untuk mengumpul koin token NeoMusic bernilai tinggi.",
                                fontSize = 12.sp,
                                color = DullGrey
                            )

                            // Task 1: Check-in
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(0.5.dp, if (!checkedInToday) NeonPurple.copy(alpha = 0.5f) else DullGrey.copy(alpha = 0.2f)), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Log Masuk Harian",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Daftar masuk hari ke-${dailyStreak + 1}",
                                            fontSize = 12.sp,
                                            color = DullGrey
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "+15 Koin & +1 Streak",
                                            fontSize = 11.sp,
                                            color = BrightGold,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (!checkedInToday) {
                                                checkedInToday = true
                                                coinCount += 15
                                                dailyStreak += 1
                                                Toast.makeText(context, "Misi Log Masuk Berjaya! +15 Koin", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        enabled = !checkedInToday,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonPurple,
                                            disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                        ),
                                        modifier = Modifier.testTag("task_checkin_button")
                                    ) {
                                        Text(
                                            text = if (checkedInToday) "Selesai" else "Mula",
                                            fontSize = 12.sp,
                                            color = if (checkedInToday) DullGrey else Color.White
                                        )
                                    }
                                }
                            }

                            // Task 2: Listening time
                            val listenerTarget = 60 // 60 seconds of simulated stream listening
                            val listenerProgress = totalSecondsListened.coerceAtMost(listenerTarget)
                            val isListeningTargetReached = totalSecondsListened >= listenerTarget
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(0.5.dp, if (isListeningTargetReached && !listeningRewardClaimed) NeonGold.copy(alpha = 0.5f) else DullGrey.copy(alpha = 0.2f)), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Dengar Lagu 1 Minit",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Masa aktif: $listenerProgress / $listenerTarget saat",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                if (isListeningTargetReached && !listeningRewardClaimed) {
                                                    listeningRewardClaimed = true
                                                    coinCount += 25
                                                    Toast.makeText(context, "Ganjaran Dengar Ganjil! +25 Koin", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = isListeningTargetReached && !listeningRewardClaimed,
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = NeonGold,
                                                disabledContainerColor = Color.White.copy(alpha = 0.05f)
                                            ),
                                            modifier = Modifier.testTag("task_listen_button")
                                        ) {
                                            Text(
                                                text = when {
                                                    listeningRewardClaimed -> "Dituntut"
                                                    isListeningTargetReached -> "Tuntut Koin"
                                                    else -> "Belum Siap"
                                                },
                                                fontSize = 11.sp,
                                                color = if (isListeningTargetReached && !listeningRewardClaimed) DeepBlack else DullGrey
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    
                                    // Progress bar
                                    LinearProgressIndicator(
                                        progress = { listenerProgress.toFloat() / listenerTarget.toFloat() },
                                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                                        color = NeonOrchid,
                                        trackColor = Color.White.copy(alpha = 0.05f)
                                    )
                                }
                            }

                            // Task 3: Secret Promo Code Input
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(0.5.dp, NeonOrchid.copy(alpha = 0.3f)), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Masukkan Kod Rahsia Kripto",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Hubungi komuniti di media sosial untuk mendapatkan kod bonus (e.g. NEOMUSIC)",
                                        fontSize = 11.sp,
                                        color = DullGrey
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextField(
                                            value = codeInput,
                                            onValueChange = { codeInput = it },
                                            placeholder = { Text("Mesej Kod...", color = DullGrey, fontSize = 12.sp) },
                                            singleLine = true,
                                            colors = TextFieldDefaults.colors(
                                                focusedTextColor = Color.White,
                                                unfocusedTextColor = Color.White,
                                                focusedContainerColor = DeepBlack,
                                                unfocusedContainerColor = DeepBlack,
                                                cursorColor = NeonPurple,
                                                focusedIndicatorColor = NeonPurple,
                                                unfocusedIndicatorColor = Color.Transparent
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("task_promo_input")
                                        )

                                        Button(
                                            onClick = {
                                                if (codeInput.uppercase() == "NEOMUSIC" && !codeClaimed) {
                                                    codeClaimed = true
                                                    coinCount += 50
                                                    Toast.makeText(context, "Kod Sah! +50 Koin Diperoleh!", Toast.LENGTH_SHORT).show()
                                                } else if (codeClaimed) {
                                                    Toast.makeText(context, "Kod sudah pernah ditebus!", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    Toast.makeText(context, "Kod salah! Guna NEOMUSIC", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            enabled = codeInput.isNotBlank() && !codeClaimed,
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonPurple)
                                        ) {
                                            Text("Tuntut", fontSize = 12.sp, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    2 -> {
                        // === TAB 3: DOMPET KRYPTO ===
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("wallet_tab")
                        ) {
                            Text(
                                text = "Dompet Kripto Desentralisasi",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightGrey
                            )

                            // Main Wallet Card
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, Brush.horizontalGradient(listOf(NeonPurple, NeonGold))), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "NEO PROTOCOL HOLDING",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = NeonGold,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "BSC MAINNET",
                                            fontSize = 10.sp,
                                            color = CyberCyan
                                        )
                                    }

                                    // Dynamic Token Calculation based on coins
                                    val neoTokens = coinCount / 10.0
                                    val myrValue = neoTokens * 4.45

                                    Column {
                                        Text(
                                            text = String.format("%.2f NEO", neoTokens),
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontFamily = FontFamily.Monospace
                                        )
                                        Text(
                                            text = String.format("≈ RM %.2f (MYR)", myrValue),
                                            fontSize = 14.sp,
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Divider(color = Color.White.copy(alpha = 0.05f))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Alamat: 0x937f2...e32a67",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = DullGrey
                                        )
                                        Text(
                                            text = "SALIN",
                                            fontSize = 11.sp,
                                            color = NeonPurple,
                                            modifier = Modifier
                                                .clickable {
                                                    Toast.makeText(context, "Alamat disalin!", Toast.LENGTH_SHORT).show()
                                                }
                                                .padding(4.dp),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Price Action Line chart
                            Text(
                                text = "Graf Harga NEO/USDT (7 Hari Sahaja)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = LightGrey
                            )
                            
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    NeoPriceLineChart(neonGlowEnabled)
                                }
                            }

                            // Converter Section
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tukar Koin Ke Token NEO",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "Kadar tukaran: 10 Koin = 1 NEO Token",
                                            fontSize = 11.sp,
                                            color = DullGrey
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            if (coinCount >= 20) {
                                                Toast.makeText(context, "Tukaran berjaya di blockchain!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Koin tidak mencukupi (Min 20)!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = NeonGold)
                                    ) {
                                        Text("Swap", fontSize = 12.sp, color = DeepBlack, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        // === TAB 4: PROFIL ===
                        Column(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth().testTag("profile_tab")
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(BorderStroke(1.dp, NeonPurple.copy(alpha = 0.4f)), RoundedCornerShape(16.dp)),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Circular glowing avatar decoration
                                    Box(
                                        modifier = Modifier
                                            .size(80.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF15121F))
                                            .border(BorderStroke(2.dp, neonBorderBrush), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "User Head",
                                            tint = NeonPurple,
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    // Email and tag
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "Neo Listener",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = "cipmore67@gmail.com",
                                            fontSize = 12.sp,
                                            color = NeonPurple,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    // Meta stats grid
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${totalSecondsListened}s",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Dengar",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${coinCount}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = BrightGold
                                            )
                                            Text(
                                                text = "Koin",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Cyber DJ",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = CyberCyan
                                            )
                                            Text(
                                                text = "Aras Peringkat",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }
                                    }
                                }
                            }

                            // Application Settings / Preferences UI
                            Text(
                                text = "Konfigurasi Estetika & Sistem",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LightGrey,
                                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = CardSurface)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Toggle 1: Aesthetic neon borders glow selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Cahaya Neon Premium (Glow)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Aktifkan visual sempadan neon yang menyinar",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }
                                        Switch(
                                            checked = neonGlowEnabled,
                                            onCheckedChange = { neonGlowEnabled = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = NeonPurple,
                                                checkedTrackColor = NeonPurple.copy(alpha = 0.3f)
                                            )
                                        )
                                    }

                                    Divider(color = Color.White.copy(alpha = 0.05f))

                                    // Toggle 2: Hi-Fi Quality
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Kualiti Audio Hi-Fi (24-bit PCM)",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Gunakan kualiti bit-rate lossless tinggi",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }
                                        Switch(
                                            checked = highFiEnabled,
                                            onCheckedChange = { highFiEnabled = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = CyberCyan,
                                                checkedTrackColor = CyberCyan.copy(alpha = 0.3f)
                                            )
                                        )
                                    }

                                    Divider(color = Color.White.copy(alpha = 0.05f))

                                    // Toggle 3: Autoplay next track
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Main Automatik Seterusnya",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color.White
                                            )
                                            Text(
                                                text = "Lompat ke lagu seterusnya apabila selesai",
                                                fontSize = 11.sp,
                                                color = DullGrey
                                            )
                                        }
                                        Switch(
                                            checked = autoPlayNext,
                                            onCheckedChange = { autoPlayNext = it },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = NeonOrchid,
                                                checkedTrackColor = NeonOrchid.copy(alpha = 0.3f)
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Format Seconds to MM:SS
fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

// 1. Custom Gold Coin Icon using Raw Canvas Vector drawing
@Composable
fun NeoCoinIcon(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        
        // Metallic circular golden gradient
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFEEAA), Color(0xFFFFB700), Color(0xFFC68600)),
                center = center,
                radius = w / 2f
            )
        )
        
        // Inner thin circle
        drawCircle(
            color = Color(0xFFFFD700),
            radius = w / 2.6f,
            style = Stroke(width = 1.5.dp.toPx())
        )
        
        // Embossed Letter 'N' path
        val nPath = Path().apply {
            moveTo(w * 0.38f, h * 0.68f)
            lineTo(w * 0.38f, h * 0.32f)
            lineTo(w * 0.62f, h * 0.68f)
            lineTo(w * 0.62f, h * 0.32f)
        }
        
        drawPath(
            path = nPath,
            color = Color.White,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

// 2. Custom Flame/Streak Icon using Raw Canvas Bezier curved path
@Composable
fun NeoFlameIcon(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        
        val flamePath = Path().apply {
            moveTo(w * 0.5f, h * 0.12f)
            // Left flame boundaries
            cubicTo(w * 0.2f, h * 0.45f, w * 0.15f, h * 0.72f, w * 0.3f, h * 0.88f)
            cubicTo(w * 0.4f, h * 0.98f, w * 0.6f, h * 0.98f, w * 0.7f, h * 0.88f)
            // Right flame boundaries and center carve
            cubicTo(w * 0.85f, h * 0.72f, w * 0.8f, h * 0.45f, w * 0.5f, h * 0.12f)
            close()
        }

        // Beautiful hot vertical gradient representing flame
        drawPath(
            path = flamePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFF1B54), Color(0xFFFF5200), Color(0xFFFFAA00))
            )
        )
        
        // Fire inner core
        val corePath = Path().apply {
            moveTo(w * 0.5f, h * 0.45f)
            cubicTo(w * 0.35f, h * 0.62f, w * 0.35f, h * 0.75f, w * 0.5f, h * 0.85f)
            cubicTo(w * 0.65f, h * 0.75f, w * 0.65f, h * 0.62f, w * 0.5f, h * 0.45f)
        }
        
        drawPath(
            path = corePath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFAA00), Color(0xFFFFD700))
            )
        )
    }
}

// Custom Rewind Icon Path
@Composable
fun NeoRewindIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        
        val rewindPath = Path().apply {
            // First triangle
            moveTo(w * 0.45f, h * 0.5f)
            lineTo(w * 0.82f, h * 0.24f)
            lineTo(w * 0.82f, h * 0.76f)
            close()
            // Second triangle
            moveTo(w * 0.12f, h * 0.5f)
            lineTo(w * 0.49f, h * 0.24f)
            lineTo(w * 0.49f, h * 0.76f)
            close()
        }
        drawPath(path = rewindPath, color = tint)
    }
}

// Custom Skip Next Icon Path
@Composable
fun NeoSkipIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        
        val skipPath = Path().apply {
            moveTo(w * 0.15f, h * 0.24f)
            lineTo(w * 0.55f, h * 0.5f)
            lineTo(w * 0.15f, h * 0.76f)
            close()
        }
        
        drawPath(path = skipPath, color = tint)
        
        // Right bar
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.68f, h * 0.24f),
            size = Size(w * 0.14f, h * 0.52f),
            cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
        )
    }
}

// Custom Play Icon Path
@Composable
fun NeoPlayIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        
        val path = Path().apply {
            moveTo(w * 0.33f, h * 0.22f)
            lineTo(w * 0.83f, h * 0.50f)
            lineTo(w * 0.33f, h * 0.78f)
            close()
        }
        drawPath(path = path, color = tint)
    }
}

// Custom Pause Icon Path
@Composable
fun NeoPauseIcon(modifier: Modifier = Modifier, size: Dp = 24.dp, tint: Color = Color.White) {
    Canvas(modifier = modifier.size(size)) {
        val w = size.toPx()
        val h = size.toPx()
        
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.26f, h * 0.22f),
            size = Size(w * 0.16f, h * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.58f, h * 0.22f),
            size = Size(w * 0.16f, h * 0.56f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )
    }
}

// Rotating Vinyl/CD Simulation
@Composable
fun VinylSpindleAnimation(isPlaying: Boolean, accentColor: Color) {
    var rotationAngle by remember { mutableStateOf(0f) }
    
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                delay(16L) // ~60fps
                rotationAngle = (rotationAngle + 1.2f) % 360f
            }
        }
    }
    
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer(rotationZ = rotationAngle)) {
        val w = size.width
        val h = size.height
        
        // Draw vinyl lines
        drawCircle(
            color = Color(0xFF16151B),
            radius = w / 2f
        )
        
        // Concentric vinyl ridges
        for (i in 1..4) {
            drawCircle(
                color = Color.White.copy(alpha = 0.04f),
                radius = (w / 2f) - (i * 12.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
        
        // Neon color reflection arc
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.4f), Color.Transparent)
            ),
            startAngle = 0f,
            sweepAngle = 120f,
            useCenter = true
        )
        drawArc(
            brush = Brush.sweepGradient(
                colors = listOf(Color.Transparent, accentColor.copy(alpha = 0.4f), Color.Transparent)
            ),
            startAngle = 180f,
            sweepAngle = 120f,
            useCenter = true
        )
    }
}

// Rotating visualizer helper helper wrapper
private fun DrawScopeMarker() {}

// Custom Audio Spectrum Visualizer
@Composable
fun NeonSpectrumVisualizer(isPlaying: Boolean, tint: Color) {
    val barCount = 18
    val frequencies = remember { mutableStateListOf<Float>().apply { addAll(List(barCount) { 0.2f }) } }
    
    // Animate lines
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                delay(80L)
                for (i in 0 until barCount) {
                    frequencies[i] = Random.nextFloat().coerceIn(0.15f, 0.95f)
                }
            }
        } else {
            for (i in 0 until barCount) {
                frequencies[i] = 0.15f
            }
        }
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        frequencies.forEach { progress ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(progress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(tint, tint.copy(alpha = 0.2f))
                        )
                    )
            )
        }
    }
}

// 3. Custom Decent Price Line Chart using raw Canvas drawing and glows
@Composable
fun NeoPriceLineChart(glowEnabled: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        val gridLines = 5
        val gridStepY = h / gridLines
        val gridStepX = w / 6

        // Draw background GRID lines
        for (i in 0..gridLines) {
            val y = i * gridStepY
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1.dp.toPx()
            )
        }
        for (i in 0..6) {
            val x = i * gridStepX
            drawLine(
                color = Color.White.copy(alpha = 0.05f),
                start = Offset(x, 0f),
                end = Offset(x, h),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Mock chart prices representation in 7 days
        val mockDataPoints = listOf(
            Offset(0f, h * 0.82f),
            Offset(gridStepX * 1f, h * 0.65f),
            Offset(gridStepX * 2f, h * 0.75f),
            Offset(gridStepX * 3f, h * 0.35f),
            Offset(gridStepX * 4f, h * 0.52f),
            Offset(gridStepX * 5f, h * 0.22f),
            Offset(w, h * 0.15f) // Glowing climax peak
        )

        // Draw Glowing bottom Area
        val areaPath = Path().apply {
            moveTo(0f, h)
            lineTo(mockDataPoints[0].x, mockDataPoints[0].y)
            for (i in 1 until mockDataPoints.size) {
                cubicTo(
                    (mockDataPoints[i-1].x + mockDataPoints[i].x) / 2f, mockDataPoints[i-1].y,
                    (mockDataPoints[i-1].x + mockDataPoints[i].x) / 2f, mockDataPoints[i].y,
                    mockDataPoints[i].x, mockDataPoints[i].y
                )
            }
            lineTo(w, h)
            close()
        }

        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(NeonPurple.copy(alpha = 0.3f), Color.Transparent)
            )
        )

        // Draw main price line curve
        val linePath = Path().apply {
            moveTo(mockDataPoints[0].x, mockDataPoints[0].y)
            for (i in 1 until mockDataPoints.size) {
                cubicTo(
                    (mockDataPoints[i-1].x + mockDataPoints[i].x) / 2f, mockDataPoints[i-1].y,
                    (mockDataPoints[i-1].x + mockDataPoints[i].x) / 2f, mockDataPoints[i].y,
                    mockDataPoints[i].x, mockDataPoints[i].y
                )
            }
        }

        // Draw outer glow outline
        if (glowEnabled) {
            drawPath(
                path = linePath,
                color = NeonGold.copy(alpha = 0.4f),
                style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawPath(
            path = linePath,
            color = NeonGold,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw current price nodes
        mockDataPoints.forEachIndexed { idx, point ->
            val nodeColor = if (idx == mockDataPoints.size - 1) Color.White else NeonPurple
            drawCircle(
                color = nodeColor,
                radius = 4.dp.toPx(),
                center = point
            )
            drawCircle(
                color = nodeColor.copy(alpha = 0.4f),
                radius = 8.dp.toPx(),
                center = point,
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

// Gradient brush generator for Profile elements
@Composable
fun NeonAndGoldGradientBrush(): Brush {
    return Brush.horizontalGradient(
        colors = listOf(NeonPurple, NeonOrchid, NeonGold)
    )
}

// Utility to rotate lines of Canvas
fun drawContextRotatedLines() {}
