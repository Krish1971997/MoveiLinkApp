package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.MovieRecord
import com.example.data.ZohoPreferences
import com.example.ui.theme.*
import com.example.viewmodel.ImportingState
import com.example.viewmodel.MovieViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: MovieViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // ViewModel States
    val movies by viewModel.filteredMovies.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val importingState by viewModel.importingState.collectAsStateWithLifecycle()
    val allMovies by viewModel.allMovies.collectAsStateWithLifecycle()

    // Dedicated search page states
    var searchTabQuery by remember { mutableStateOf("") }
    var searchTabSelectedCategory by remember { mutableStateOf<String?>(null) }
    var searchTabSelectedStatus by remember { mutableStateOf<String?>("All") }
    var searchTabSelectedMovieIds by remember { mutableStateOf(setOf<Int>()) }
    var searchHistory by remember { mutableStateOf(listOf("2026", "Amaran", "Coolie", "Vettaiyan")) }


    // Interactive UI states mirroring the bubble mockup
    var showAddDialog by remember { mutableStateOf(false) }
    var showOptionsDropdown by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    
    var activeErrorDetail by remember { mutableStateOf<String?>(null) }
    var terminalStatusText by remember { mutableStateOf("status: STANDBY\n> terminal idle.\n> waiting to fetch database workbook...") }
    var terminalStatusColor by remember { mutableStateOf(Color(0xFF94A3B8)) }
    
    // Bottom Tab state to mimic high-performance system dashboards
    var activeBottomTab by remember { mutableIntStateOf(0) } // 0: Home, 1: Files, 2: Analytics, 3: Settings
    
    // Multiselection set for checked movie lines (mirrors the checkbox task flow in mockup)
    var selectedMovieIds by remember { mutableStateOf(setOf<Int>()) }

    // Zoho state parameters
    val prefs = remember { ZohoPreferences(context) }
    var clientIdText by remember { mutableStateOf(prefs.clientId) }
    var clientSecretText by remember { mutableStateOf(prefs.clientSecret) }
    var refreshTokenText by remember { mutableStateOf(prefs.refreshToken) }
    var folderIdText by remember { mutableStateOf(prefs.folderId) }
    var fileNameText by remember { mutableStateOf(prefs.fileName) }
    var defaultExtensionText by remember { mutableStateOf(prefs.defaultExtension) }
    var accountsServerText by remember { mutableStateOf(prefs.accountsServer) }
    var apiServerText by remember { mutableStateOf(prefs.apiServer) }
    var clearOldData by remember { mutableStateOf(prefs.clearOldDataBeforeUpload) }
    var isUnlocked by remember { mutableStateOf(!prefs.isAppLockEnabled) }
    var isAppLockEnabled by remember { mutableStateOf(prefs.isAppLockEnabled) }
    var appLockPasscodeText by remember { mutableStateOf(prefs.appLockPasscode) }
    var isClientIdVisible by remember { mutableStateOf(false) }
    var isClientSecretVisible by remember { mutableStateOf(false) }
    var isRefreshTokenVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activeBottomTab) {
        if (activeBottomTab == 3) {
            val p = ZohoPreferences(context)
            clientIdText = p.clientId
            clientSecretText = p.clientSecret
            refreshTokenText = p.refreshToken
            folderIdText = p.folderId
            fileNameText = p.fileName
            defaultExtensionText = p.defaultExtension
            accountsServerText = p.accountsServer
            apiServerText = p.apiServer
            clearOldData = p.clearOldDataBeforeUpload
            isAppLockEnabled = p.isAppLockEnabled
            appLockPasscodeText = p.appLockPasscode
        }
    }

    // File picker launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importFile(context, uri)
        }
    }

    // Handles showing toasts or state-reset when import finishes
    LaunchedEffect(importingState) {
        when (val state = importingState) {
            is ImportingState.Loading -> {
                terminalStatusColor = Color(0xFFFBBF24)
                terminalStatusText = "status: CONNECTING\n> refreshing active oauth credentials...\n> scanning files inside cloud folder: ${prefs.folderId.take(12)}...\n> matching sheets naming convention for '${prefs.fileName}'...\n> downloading file contents stream..."
            }
            is ImportingState.Success -> {
                terminalStatusColor = Color(0xFF34D399)
                terminalStatusText = "status: SUCCESS\n> transaction approved.\n> downloaded movie index matched: OK\n> old records purged: ${if (prefs.clearOldDataBeforeUpload) "YES" else "NO"}\n> compiled database table: inserted ${state.count} records."
                Toast.makeText(context, "Successfully imported ${state.count} movies!", Toast.LENGTH_LONG).show()
                viewModel.resetImportState()
            }
            is ImportingState.Error -> {
                terminalStatusColor = Color(0xFFF87171)
                terminalStatusText = "status: TERMINATED_ERROR\n> error code: SYSTEM_FAULT\n> message: ${state.message}\n> database rollbacked to state: SAFE"
                activeErrorDetail = state.message
                Toast.makeText(context, "Import failed: ${state.message}", Toast.LENGTH_LONG).show()
                viewModel.resetImportState()
            }
            else -> {}
        }
    }

    // Gentle soap-bubble background floating simulation parameters
    val bubbleAnim = rememberInfiniteTransition(label = "bubbles")
    val floatX1 by bubbleAnim.animateFloat(
        initialValue = -30f, targetValue = 30f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "x1"
    )
    val floatY1 by bubbleAnim.animateFloat(
        initialValue = 0f, targetValue = -25f,
        animationSpec = infiniteRepeatable(animation = tween(5000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "y1"
    )

    if (!isUnlocked) {
        AppLockScreen(
            correctPin = prefs.appLockPasscode,
            onUnlocked = { isUnlocked = true }
        )
        return
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(R.drawable.premium_movie_link_logo_1790163614766),
                                contentDescription = "Movie Logo",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.Start) {
                            Text(
                                text = "MOVIE",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.SansSerif,
                                color = BubbleTextPrimary,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "CINEMATIC SYNC PLATFORM",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = BubbleTextSecondary,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showHelpDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.HelpOutline,
                            contentDescription = "Show Import Help",
                            tint = BubbleBlue
                        )
                    }
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Rounded.AddCircle,
                            contentDescription = "Add Movie Manually",
                            tint = BubbleBlue
                        )
                    }
                    Box {
                        IconButton(onClick = { showOptionsDropdown = true }) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More Options",
                                tint = BubbleTextSecondary
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsDropdown,
                            onDismissRequest = { showOptionsDropdown = false },
                            modifier = Modifier
                                .background(BubbleCardBg)
                                .border(1.dp, BubbleBorder, RoundedCornerShape(14.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Import Spreadsheets (.xlsx/.csv)", color = BubbleTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.CloudUpload, contentDescription = null, tint = BubbleBlue, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOptionsDropdown = false
                                    fileLauncher.launch(
                                        arrayOf(
                                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                            "text/comma-separated-values",
                                            "text/plain"
                                        )
                                    )
                                }
                            )
                            HorizontalDivider(color = BubbleBorder, modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("Refresh Sample Databases", color = BubbleTextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Cached, contentDescription = null, tint = BubbleBlue, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOptionsDropdown = false
                                    viewModel.clearAllData()
                                    Toast.makeText(context, "Index refreshed with cinema presets!", Toast.LENGTH_SHORT).show()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset Entire Index", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp)) },
                                onClick = {
                                    showOptionsDropdown = false
                                    viewModel.clearAllData()
                                    selectedMovieIds = emptySet()
                                    Toast.makeText(context, "Movie database entirely wiped!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = BubbleBg.copy(alpha = 0.85f)
                )
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(BubbleBg)
                .drawBehind {
                    // Draw Soap Bubble 1 (Cyan Iridescent)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = IridescentBubble1,
                            radius = 200.dp.toPx()
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.1f + floatX1.dp.toPx(),
                            size.height * 0.15f + floatY1.dp.toPx()
                        )
                    )

                    // Draw Soap Bubble 2 (Purple/Violet Iridescent)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = IridescentBubble2,
                            radius = 160.dp.toPx()
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.88f - floatX1.dp.toPx() * 0.5f,
                            size.height * 0.65f + floatY1.dp.toPx() * 0.8f
                        )
                    )

                    // Draw Soap Bubble 3 (Greenish Highlight)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = IridescentBubble3,
                            radius = 120.dp.toPx()
                        ),
                        center = androidx.compose.ui.geometry.Offset(
                            size.width * 0.15f + floatX1.dp.toPx() * 1.2f,
                            size.height * 0.82f - floatY1.dp.toPx() * 0.6f
                        )
                    )
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Secondary file import bar progress indicator
                if (importingState is ImportingState.Loading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = BubbleBlue,
                        trackColor = Color.Transparent
                    )
                }

                when (activeBottomTab) {
                    2 -> {
                        // 1. Top Premium Promo Banner matching image "Upgrade to Pro" card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Brush.horizontalGradient(BubblePremiumGradient))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        Brush.horizontalGradient(listOf(Color(0x33FFFFFF), Color(0x11FFFFFF)))
                                    ), RoundedCornerShape(20.dp)
                                )
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(20.dp),
                                    ambientColor = BubbleBlue,
                                    spotColor = BubbleBlue
                                )
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(horizontal = 16.dp, vertical = 14.dp)
                                    .fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Golden Crown Indicator
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(GoldPremiumGradient)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.WorkspacePremium,
                                        contentDescription = "Premium Badge",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upgrade to Pro Sync Hub",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Unlock smart filters & batch clipboard exports",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color.White)
                                        .clickable {
                                            Toast
                                                .makeText(
                                                    context,
                                                    "Pro Sync features activated in manual sandbox!",
                                                    Toast.LENGTH_SHORT
                                                )
                                                .show()
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Upgrade",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BubbleBlue,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Analytics View (Tab Index 1) OR Capacity Gauges
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Left Gauge Card: Circular Glass Index Capacity Gauge (matches the clean center circles)
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BubbleCardBg)
                                    .border(1.dp, BubbleBorder, RoundedCornerShape(20.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "INDEX SPACE",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = BubbleTextSecondary,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    
                                    Box(
                                        modifier = Modifier.size(80.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // Dynamic progress calculation
                                        val maxLimit = 250f
                                        val currentCount = movies.size.toFloat()
                                        val progress = (currentCount / maxLimit).coerceIn(0f, 1f)
                                        val animatedProgress by animateFloatAsState(
                                            targetValue = progress,
                                            animationSpec = tween(durationMillis = 1000),
                                            label = "gauge"
                                        )
                                        
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            // Track circle
                                            drawCircle(
                                                color = BubbleBorder,
                                                radius = size.minDimension / 2,
                                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                            // Animated arc
                                            drawArc(
                                                brush = Brush.sweepGradient(BubbleGaugeGradient),
                                                startAngle = -90f,
                                                sweepAngle = animatedProgress * 360f,
                                                useCenter = false,
                                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "${(progress * 100).toInt()}%",
                                                fontWeight = FontWeight.Black,
                                                color = BubbleTextPrimary,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "indexed",
                                                fontSize = 8.sp,
                                                color = BubbleTextMuted
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "${movies.size} of 250 links used",
                                        fontWeight = FontWeight.SemiBold,
                                        color = BubbleTextPrimary,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Right Side Description Card: Dynamic optimize block
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(BubbleCardBg)
                                    .border(1.dp, BubbleBorder, RoundedCornerShape(20.dp))
                                    .padding(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Batch Link Optimizer",
                                        fontWeight = FontWeight.Bold,
                                        color = BubbleTextPrimary,
                                        fontSize = 13.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Selected: ${selectedMovieIds.size} files ready to be copied or sanitized below. Automapping aligns your Excel files.",
                                        color = BubbleTextSecondary,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(BubbleBorder),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.VerifiedUser,
                                                contentDescription = null,
                                                tint = BubbleBlue,
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Auto Clean Secure",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BubbleTextPrimary
                                        )
                                    }
                                }
                            }
                        }

                        // 2. Beautiful Glass Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("search_bar"),
                            placeholder = { Text("Search title, year, category...", color = BubbleTextMuted, fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Search Icon", tint = BubbleBlue) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = BubbleTextSecondary)
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            shape = RoundedCornerShape(26.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BubbleTextPrimary,
                                unfocusedTextColor = BubbleTextPrimary,
                                focusedBorderColor = BubbleBlue,
                                unfocusedBorderColor = BubbleBorder,
                                focusedContainerColor = BubbleCardBg,
                                unfocusedContainerColor = BubbleCardBg.copy(alpha = 0.9f)
                            )
                        )

                        // 3. Category Horizontal Chips (styled as white cards with soft borders)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp, horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            item {
                                val isSelected = selectedCategory == null
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) BubbleBlue else BubbleCardBg)
                                        .border(BorderStroke(1.dp, if (isSelected) BubbleBlue else BubbleBorder), RoundedCornerShape(16.dp))
                                        .clickable { viewModel.selectCategory(null) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Category,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else BubbleBlue,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "All Categories",
                                            color = if (isSelected) Color.White else BubbleTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            items(categories) { category ->
                                val isSelected = selectedCategory == category
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(if (isSelected) BubbleBlue else BubbleCardBg)
                                        .border(BorderStroke(1.dp, if (isSelected) BubbleBlue else BubbleBorder), RoundedCornerShape(16.dp))
                                        .clickable { viewModel.selectCategory(category) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) Color.White else BubbleTextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Quick Deselect/Select All Actions above cards
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 18.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${movies.size} records match filters",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = BubbleTextSecondary
                            )
                            
                            if (movies.isNotEmpty()) {
                                Text(
                                    text = if (selectedMovieIds.size == movies.size) "Deselect all" else "Select all",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    color = BubbleBlue,
                                    modifier = Modifier.clickable {
                                        if (selectedMovieIds.size == movies.size) {
                                            selectedMovieIds = emptySet()
                                        } else {
                                            selectedMovieIds = movies.map { it.id }.toSet()
                                        }
                                    }
                                )
                            }
                        }

                        // 4. Movie List matching visual list cards
                        if (movies.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(90.dp)
                                            .clip(CircleShape)
                                            .background(BubbleCardBg)
                                            .border(1.dp, BubbleBorder, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.MovieFilter,
                                            contentDescription = "Empty icon",
                                            tint = BubbleTextSecondary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Index Portal Empty",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 17.sp,
                                        color = BubbleTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Upload a workbook (.xlsx), CSV, or refresh to recover presets.",
                                        color = BubbleTextSecondary,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 24.dp)
                                    )
                                    Spacer(modifier = Modifier.height(20.dp))
                                    Button(
                                        onClick = {
                                            fileLauncher.launch(
                                                arrayOf(
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "text/comma-separated-values",
                                                    "text/plain"
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BubbleBlue, contentColor = Color.White),
                                        shape = RoundedCornerShape(18.dp)
                                    ) {
                                        Icon(Icons.Rounded.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Import File", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("movie_list"),
                                contentPadding = PaddingValues(bottom = 120.dp, top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(movies, key = { it.id }) { movie ->
                                    val isSelected = selectedMovieIds.contains(movie.id)
                                    MovieCardItem(
                                        movie = movie,
                                        isChecked = isSelected,
                                        onCheckToggle = {
                                            selectedMovieIds = if (isSelected) {
                                                selectedMovieIds - movie.id
                                            } else {
                                                selectedMovieIds + movie.id
                                            }
                                        },
                                        onDelete = { viewModel.deleteMovie(movie.id) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Zoho Sync and Import hub
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BubbleBorder, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = BubbleCardBg)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(BubbleBlue.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.CloudDownload,
                                            contentDescription = null,
                                            tint = BubbleBlue,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Cloud API Sync Portal",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = BubbleTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Directly download, parse, and commit matching sheets from remote Workdrive.",
                                        fontSize = 12.sp,
                                        color = BubbleTextSecondary,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(horizontal = 14.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    HorizontalDivider(color = BubbleBorder)
                                    Spacer(modifier = Modifier.height(14.dp))
                                    
                                    // Target info brief
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceAround
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("TARGET FOLDER ID", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BubbleTextMuted)
                                            Text(prefs.folderId.take(10).plus("...").ifEmpty { "None" }, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BubbleTextPrimary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("MATCH FILE NAME", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BubbleTextMuted)
                                            Text(prefs.fileName.ifEmpty { "movies" } + "." + prefs.defaultExtension, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BubbleTextPrimary)
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("CLEANS DATA", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BubbleTextMuted)
                                            Text(if (prefs.clearOldDataBeforeUpload) "YES" else "NO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if(prefs.clearOldDataBeforeUpload) BubbleGreen else BubbleBlue)
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(20.dp))
                                    
                                    Button(
                                        onClick = { viewModel.syncFromZoho(context) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(44.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Unspecified),
                                        contentPadding = PaddingValues(),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Brush.horizontalGradient(CleanBtnGradient)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color.White)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text("Fetch & Sync Cloud Sheet", fontWeight = FontWeight.ExtraBold, color = Color.White)
                                            }
                                        }
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BubbleBorder, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = BubbleCardBg)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(16.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(BubbleGreen.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.UploadFile,
                                            contentDescription = null,
                                            tint = BubbleGreen,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Manual Upload", fontWeight = FontWeight.Bold, color = BubbleTextPrimary, fontSize = 13.sp)
                                        Text("Select localized CSV/XLSX workbook files.", color = BubbleTextSecondary, fontSize = 11.sp)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Button(
                                        onClick = {
                                            fileLauncher.launch(
                                                arrayOf(
                                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                                    "text/comma-separated-values",
                                                    "text/plain"
                                                )
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = BubbleGreen),
                                        shape = RoundedCornerShape(14.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text("Browse", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BubbleBorder, RoundedCornerShape(20.dp)),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Rounded.Terminal, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("API Connection Terminal", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.weight(1f))
                                        
                                        TextButton(
                                            onClick = {
                                                activeErrorDetail = terminalStatusText
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(24.dp)
                                        ) {
                                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Expand", color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 180.dp)
                                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                activeErrorDetail = terminalStatusText
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                            Text(
                                                text = terminalStatusText,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = terminalStatusColor,
                                                lineHeight = 16.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            if (terminalStatusColor == Color(0xFFF87171)) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "⚠️ Tap here to expand & copy full error message ⚠️",
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = Color(0xFFF87171).copy(alpha = 0.8f),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(80.dp))
                        }
                    }
                    0 -> {
                        // High-performance search & filter panel
                        val searchTabFilteredMovies = remember(allMovies, searchTabQuery, searchTabSelectedCategory, searchTabSelectedStatus) {
                            allMovies.filter { movie ->
                                val matchesQuery = searchTabQuery.isEmpty() ||
                                        movie.name.contains(searchTabQuery, ignoreCase = true) ||
                                        movie.category.contains(searchTabQuery, ignoreCase = true) ||
                                        movie.sublink.contains(searchTabQuery, ignoreCase = true)
                                val matchesCategory = searchTabSelectedCategory == null || movie.category.equals(searchTabSelectedCategory, ignoreCase = true)
                                
                                val isTamilYear = movie.category.contains("2026")
                                val matchesStatus = when (searchTabSelectedStatus) {
                                    "Completed" -> isTamilYear
                                    "In process" -> !isTamilYear
                                    else -> true
                                }
                                matchesQuery && matchesCategory && matchesStatus
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp)
                        ) {
                            // Search tab header with status overview count
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Portal Search Engine",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 16.sp,
                                        color = BubbleTextPrimary
                                    )
                                    Text(
                                        text = "Query and compile specific movie target records",
                                        fontSize = 11.sp,
                                        color = BubbleTextSecondary
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BubbleBlue.copy(alpha = 0.12f))
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "${searchTabFilteredMovies.size} Matches",
                                        fontWeight = FontWeight.Bold,
                                        color = BubbleBlue,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // 1. Sleek Search TextField
                            OutlinedTextField(
                                value = searchTabQuery,
                                onValueChange = { searchTabQuery = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                    .testTag("movie_search_tab_input"),
                                placeholder = { Text("Filter title, keyword, year, path...", color = BubbleTextMuted, fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = "Tab Search", tint = BubbleBlue) },
                                trailingIcon = {
                                    if (searchTabQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchTabQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear search", tint = BubbleTextSecondary)
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(26.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = BubbleTextPrimary,
                                    unfocusedTextColor = BubbleTextPrimary,
                                    focusedBorderColor = BubbleBlue,
                                    unfocusedBorderColor = BubbleBorder,
                                    focusedContainerColor = BubbleCardBg,
                                    unfocusedContainerColor = BubbleCardBg.copy(alpha = 0.9f)
                                )
                            )

                            // 2. Clickable Quick History Tags
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    Text(
                                        text = "Recent Searches:",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BubbleTextMuted,
                                        modifier = Modifier.padding(end = 4.dp)
                                    )
                                }
                                items(searchHistory) { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(BubbleBorder.copy(alpha = 0.5f))
                                            .clickable { searchTabQuery = tag }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(text = tag, color = BubbleTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // 3. Category selector chips line
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                item {
                                    val isSelected = searchTabSelectedCategory == null
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) BubbleBlue else BubbleCardBg)
                                            .border(BorderStroke(1.dp, if (isSelected) BubbleBlue else BubbleBorder), RoundedCornerShape(16.dp))
                                            .clickable { searchTabSelectedCategory = null }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "All Categories",
                                            color = if (isSelected) Color.White else BubbleTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                items(categories) { category ->
                                    val isSelected = searchTabSelectedCategory == category
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) BubbleBlue else BubbleCardBg)
                                            .border(BorderStroke(1.dp, if (isSelected) BubbleBlue else BubbleBorder), RoundedCornerShape(16.dp))
                                            .clickable { searchTabSelectedCategory = category }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = category,
                                            color = if (isSelected) Color.White else BubbleTextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // 4. Status filters: All, Completed, In process
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Status Flow:",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BubbleTextMuted
                                )
                                listOf("All", "Completed", "In process").forEach { status ->
                                    val isSelected = searchTabSelectedStatus == status
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (isSelected) BubbleBlue.copy(alpha = 0.15f) else Color.Transparent)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    if (isSelected) BubbleBlue else Color.Transparent
                                                ), RoundedCornerShape(12.dp)
                                            )
                                            .clickable { searchTabSelectedStatus = status }
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = status,
                                            color = if (isSelected) BubbleBlue else BubbleTextSecondary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }

                            // 5. Select All & Deselect & Batch Actions bar
                            if (searchTabFilteredMovies.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isAllSelectedInSearch = searchTabSelectedMovieIds.size == searchTabFilteredMovies.size
                                    Text(
                                        text = if (isAllSelectedInSearch) "Deselect Search Results" else "Select Search Results",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BubbleBlue,
                                        modifier = Modifier.clickable {
                                            searchTabSelectedMovieIds = if (isAllSelectedInSearch) {
                                                emptySet()
                                            } else {
                                                searchTabFilteredMovies.map { it.id }.toSet()
                                            }
                                        }
                                    )

                                    if (searchTabSelectedMovieIds.isNotEmpty()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Copy ${searchTabSelectedMovieIds.size}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                color = BubbleGreen,
                                                modifier = Modifier.clickable {
                                                    val matchedSelected = searchTabFilteredMovies.filter { searchTabSelectedMovieIds.contains(it.id) }
                                                    val linksLine = matchedSelected.joinToString(separator = "\n") { it.link.ifEmpty { it.pageUrl } }
                                                    copyToClipboard(context, linksLine, "Batch Search Matches")
                                                    Toast.makeText(context, "Copied ${matchedSelected.size} search result links!", Toast.LENGTH_LONG).show()
                                                }
                                            )
                                            Text(
                                                text = "•",
                                                fontSize = 11.sp,
                                                color = BubbleTextMuted
                                            )
                                            Text(
                                                text = "Clear selections",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Red,
                                                modifier = Modifier.clickable { searchTabSelectedMovieIds = emptySet() }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // 6. Active results grid list
                            if (searchTabFilteredMovies.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Rounded.SearchOff,
                                            contentDescription = "No results",
                                            tint = BubbleTextMuted,
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "No Cinema Index Matched",
                                            fontWeight = FontWeight.Bold,
                                            color = BubbleTextPrimary,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Reframe your search phrase or categories filters",
                                            color = BubbleTextSecondary,
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 24.dp)
                                        )
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("search_tab_movie_list"),
                                    contentPadding = PaddingValues(bottom = 90.dp, top = 2.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(searchTabFilteredMovies, key = { it.id }) { movie ->
                                        val isSelected = searchTabSelectedMovieIds.contains(movie.id)
                                        MovieCardItem(
                                            movie = movie,
                                            isChecked = isSelected,
                                            onCheckToggle = {
                                                searchTabSelectedMovieIds = if (isSelected) {
                                                    searchTabSelectedMovieIds - movie.id
                                                } else {
                                                    searchTabSelectedMovieIds + movie.id
                                                }
                                            },
                                            onDelete = { viewModel.deleteMovie(movie.id) },
                                            modifier = Modifier.animateItem()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    3 -> {
                        // Credentials configuration
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Cloud Hub Connection Details",
                                fontWeight = FontWeight.Black,
                                fontSize = 15.sp,
                                color = BubbleTextPrimary,
                                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                            )
                            
                            val textFieldColors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = BubbleTextPrimary,
                                unfocusedTextColor = BubbleTextPrimary,
                                focusedBorderColor = BubbleBlue,
                                unfocusedBorderColor = BubbleBorder,
                                focusedContainerColor = BubbleCardBg,
                                unfocusedContainerColor = BubbleCardBg.copy(alpha = 0.8f)
                            )

                            OutlinedTextField(
                                value = clientIdText,
                                onValueChange = { clientIdText = it },
                                label = { Text("Client ID") },
                                placeholder = { Text("Client ID key...") },
                                modifier = Modifier.fillMaxWidth().testTag("config_client_id"),
                                visualTransformation = if (isClientIdVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { isClientIdVisible = !isClientIdVisible }) {
                                        Icon(
                                            imageVector = if (isClientIdVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                            contentDescription = if (isClientIdVisible) "Hide Client ID" else "Show Client ID",
                                            tint = BubbleTextSecondary
                                         )
                                     }
                                 },
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = clientSecretText,
                                visualTransformation = if (isClientSecretVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { isClientSecretVisible = !isClientSecretVisible }) {
                                        Icon(
                                            imageVector = if (isClientSecretVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                            contentDescription = if (isClientSecretVisible) "Hide Client Secret" else "Show Client Secret",
                                            tint = BubbleTextSecondary
                                        )
                                    }
                                },
                                onValueChange = { clientSecretText = it },
                                label = { Text("Client Secret") },
                                placeholder = { Text("Client Secret key...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                            )

                            OutlinedTextField(
                                value = refreshTokenText,
                                visualTransformation = if (isRefreshTokenVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                trailingIcon = {
                                    IconButton(onClick = { isRefreshTokenVisible = !isRefreshTokenVisible }) {
                                        Icon(
                                            imageVector = if (isRefreshTokenVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                                            contentDescription = if (isRefreshTokenVisible) "Hide Refresh Token" else "Show Refresh Token",
                                            tint = BubbleTextSecondary
                                        )
                                    }
                                },
                                onValueChange = { refreshTokenText = it },
                                label = { Text("Refresh Token") },
                                placeholder = { Text("Active Refresh Token...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = folderIdText,
                                onValueChange = { folderIdText = it },
                                label = { Text("Workdrive Folder ID") },
                                placeholder = { Text("Folder ID where excel resides...") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = fileNameText,
                                onValueChange = { fileNameText = it },
                                label = { Text("Target File Name") },
                                placeholder = { Text("e.g. movies") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            OutlinedTextField(
                                value = defaultExtensionText,
                                onValueChange = { defaultExtensionText = it },
                                label = { Text("File Extension") },
                                placeholder = { Text("e.g. xlsx") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = textFieldColors,
                                shape = RoundedCornerShape(12.dp)
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, BubbleBorder, RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = BubbleCardBg)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(14.dp)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Delete Old Data First", fontWeight = FontWeight.Bold, color = BubbleTextPrimary, fontSize = 13.sp)
                                        Text("Wipes local app listings before inserting database workbook sync rows.", color = BubbleTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Switch(
                                        checked = clearOldData,
                                        onCheckedChange = { clearOldData = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BubbleBlue)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = BubbleCardBg)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("App Lock Security PIN", fontWeight = FontWeight.Bold, color = BubbleTextPrimary, fontSize = 13.sp)
                                            Text("Require entering a numeric PIN to unlock access to all client layers.", color = BubbleTextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
                                        }
                                        Spacer(modifier = Modifier.width(14.dp))
                                        Switch(
                                            checked = isAppLockEnabled,
                                            onCheckedChange = { isAppLockEnabled = it },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = BubbleBlue)
                                        )
                                    }
                                    if (isAppLockEnabled) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        OutlinedTextField(
                                            value = appLockPasscodeText,
                                            onValueChange = { input -> 
                                                if (input.all { it.isDigit() } && input.length <= 8) {
                                                    appLockPasscodeText = input
                                                }
                                            },
                                            label = { Text("App Lock PIN Code (4-8 digits)", fontSize = 11.sp) },
                                            placeholder = { Text("e.g. 1234") },
                                            modifier = Modifier.fillMaxWidth(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            colors = textFieldColors,
                                            shape = RoundedCornerShape(12.dp),
                                            singleLine = true
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Keep this PIN safe. It will be required every time the app launches.",
                                            color = BubbleTextSecondary,
                                            fontSize = 9.sp,
                                            lineHeight = 12.sp
                                        )
                                    }
                                }
                            }

                            var showAdvanced by remember { mutableStateOf(false) }
                            Column {
                                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (showAdvanced) "Generic region settings" else "Generic region settings (Advanced Regional Hostings)", fontSize = 12.sp, color = BubbleBlue, fontWeight = FontWeight.Bold)
                                    }
                                }
                                
                                if (showAdvanced) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 4.dp)) {
                                        OutlinedTextField(
                                            value = accountsServerText,
                                            onValueChange = { accountsServerText = it },
                                            label = { Text("Regional Accounts Server Host") },
                                            placeholder = { Text("https://accounts.cloudworkdrive.com") },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = textFieldColors,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        OutlinedTextField(
                                            value = apiServerText,
                                            onValueChange = { apiServerText = it },
                                            label = { Text("Regional Workdrive API Host") },
                                            placeholder = { Text("https://workdrive.api-provider.com") },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = textFieldColors,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    val p = ZohoPreferences(context)
                                    
                                    // PIN validation check before saving
                                    if (isAppLockEnabled) {
                                        val pin = appLockPasscodeText.trim()
                                        if (pin.length < 4) {
                                            Toast.makeText(context, "ERROR: Security PIN code is too short! Must be at least 4 digits.", Toast.LENGTH_LONG).show()
                                            return@Button
                                        }
                                    }

                                    p.clientId = clientIdText.trim()
                                    p.clientSecret = clientSecretText.trim()
                                    p.refreshToken = refreshTokenText.trim()
                                    p.folderId = folderIdText.trim()
                                    p.fileName = fileNameText.trim()
                                    p.defaultExtension = defaultExtensionText.trim()
                                    p.accountsServer = accountsServerText.trim()
                                    p.apiServer = apiServerText.trim()
                                    p.clearOldDataBeforeUpload = clearOldData
                                    p.isAppLockEnabled = isAppLockEnabled
                                    p.appLockPasscode = appLockPasscodeText.trim().ifEmpty { "1234" }
                                    
                                    Toast.makeText(context, "Keys and security configurations secured!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BubbleBlue),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save Configuration Parameters", fontWeight = FontWeight.Bold)
                            }
                            
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            }

            // 5. Giant Blue Gradient Bottom Action Button
            if (activeBottomTab == 2) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(bottom = 60.dp)
                        .padding(horizontal = 14.dp)
                ) {
                    val isAnyChecked = selectedMovieIds.isNotEmpty()
                    Button(
                        onClick = {
                            if (isAnyChecked) {
                                val checkedRecords = movies.filter { selectedMovieIds.contains(it.id) }
                                val linksLine = checkedRecords.joinToString(separator = "\n") { it.link.ifEmpty { it.pageUrl } }
                                copyToClipboard(context, linksLine, "Batch Links")
                                Toast.makeText(context, "Copied ${checkedRecords.size} links directly to clipboard!", Toast.LENGTH_LONG).show()
                            } else {
                                fileLauncher.launch(
                                    arrayOf(
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "text/comma-separated-values",
                                        "text/plain"
                                    )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .shadow(
                                elevation = if (isAnyChecked) 6.dp else 2.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = BubbleBlue,
                                spotColor = BubbleBlue
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Unspecified
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        if (isAnyChecked) CleanBtnGradient else listOf(Color(0xFF3B82F6), Color(0xFF60A5FA))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isAnyChecked) Icons.Rounded.DoneAll else Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isAnyChecked) "Copy Selected (${selectedMovieIds.size} links)" else "Import New Movie File",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }

            // 6. Beautiful Bottom Tab Navigation Bar
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(58.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .shadow(16.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
                colors = CardDefaults.cardColors(containerColor = BubbleCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    BottomTabItem(
                        icon = Icons.Rounded.Search,
                        label = "Search",
                        isActive = activeBottomTab == 0,
                        onClick = {
                            activeBottomTab = 0
                        }
                    )
                    BottomTabItem(
                        icon = Icons.Rounded.Sync,
                        label = "Sync Hub",
                        isActive = activeBottomTab == 1,
                        onClick = {
                            activeBottomTab = 1
                        }
                    )
                    BottomTabItem(
                        icon = Icons.Rounded.Home,
                        label = "Dashboard",
                        isActive = activeBottomTab == 2,
                        onClick = {
                            activeBottomTab = 2
                            viewModel.selectCategory(null)
                        }
                    )
                    BottomTabItem(
                        icon = Icons.Rounded.Settings,
                        label = "Config",
                        isActive = activeBottomTab == 3,
                        onClick = {
                            activeBottomTab = 3
                        }
                    )
                }
            }
        }
    }

    // Modernized Help Anatomy Dialog with Spreadsheet Table view
    if (showHelpDialog) {
        Dialog(onDismissRequest = { showHelpDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
                    .border(BorderStroke(1.dp, BubbleBorder), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BubbleCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.TableChart, contentDescription = null, tint = BubbleBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Spreadsheet Layout Map",
                                fontWeight = FontWeight.Black,
                                fontSize = 16.sp,
                                color = BubbleTextPrimary
                            )
                        }
                        IconButton(onClick = { showHelpDialog = false }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = BubbleTextSecondary)
                        }
                    }
                    HorizontalDivider(color = BubbleBorder, modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "The parser scans the workbook header row. If no header row is identified, columns are automatically aligned index-wise to these standard columns:",
                        fontSize = 12.sp,
                        color = BubbleTextSecondary,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    // Premium spreadsheet grid
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .background(BubbleBg, shape = RoundedCornerShape(12.dp))
                            .border(1.dp, BubbleBorder, RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        // Header row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BubbleBorder)
                                .padding(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Text("Cell Index", fontWeight = FontWeight.Bold, color = BubbleTextPrimary, fontSize = 11.sp, modifier = Modifier.width(80.dp))
                            Text("Mapped Field Name", fontWeight = FontWeight.Bold, color = BubbleTextPrimary, fontSize = 11.sp)
                        }
                        SimulatedRow("Column B", "Movie Title")
                        SimulatedRow("Column C", "Sublink Path (/dacoit...)")
                        SimulatedRow("Column D", "Category Stream (tamil-2026-movies)")
                        SimulatedRow("Column E", "Direct Hub Link URL (Priority Copy)")
                        SimulatedRow("Column F", "Landing Reference Page Link")
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = { showHelpDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = BubbleBlue, contentColor = Color.White),
                        modifier = Modifier.align(Alignment.End),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text("Acknowledge Plan", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // Detailed Connection Error / Log Inspection Dialog
    if (activeErrorDetail != null) {
        AlertDialog(
            onDismissRequest = { activeErrorDetail = null },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "System Sync Detail Log",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "The complete system details, logs, or error stack trace are displayed below for troubleshooting configuration details.",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = activeErrorDetail ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (activeErrorDetail?.contains("Error") == true || activeErrorDetail?.contains("denied") == true || activeErrorDetail?.contains("failed") == true || activeErrorDetail?.contains("TERMINATED_ERROR") == true) Color(0xFFF87171) else Color(0xFF38BDF8),
                            lineHeight = 16.sp
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val textToCopy = activeErrorDetail ?: ""
                        copyToClipboard(context, textToCopy, "System Log Detail")
                        Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Details", fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                }
            },
            dismissButton = {
                TextButton(onClick = { activeErrorDetail = null }) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.border(1.dp, BubbleBorder, RoundedCornerShape(24.dp))
        )
    }

    // Add Movie Dialog Dialog
    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var sublink by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("") }
        var link by remember { mutableStateOf("") }
        var pageUrl by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .border(BorderStroke(1.dp, BubbleBorder), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = BubbleCardBg)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, tint = BubbleBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Index Record Manually",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = BubbleTextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))

                    val textFieldColors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = BubbleTextPrimary,
                        unfocusedTextColor = BubbleTextPrimary,
                        focusedBorderColor = BubbleBlue,
                        unfocusedBorderColor = BubbleBorder,
                        focusedContainerColor = BubbleBg.copy(alpha = 0.5f),
                        unfocusedContainerColor = BubbleBg.copy(alpha = 0.5f)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Movie Title (Col B)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("add_movie_name"),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = sublink,
                        onValueChange = { sublink = it },
                        label = { Text("Sublink Path (Col C)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Category (Col D)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link URL (Col E)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    OutlinedTextField(
                        value = pageUrl,
                        onValueChange = { pageUrl = it },
                        label = { Text("Portal Page URL (Col F)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = textFieldColors,
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Discard", color = BubbleTextSecondary, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                if (name.isBlank()) {
                                    Toast.makeText(context, "Movie title is required!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addManualMovie(
                                        name = name,
                                        sublink = sublink,
                                        category = category,
                                        link = link,
                                        pageUrl = pageUrl
                                    ) {
                                        coroutineScope.launch {
                                            showAddDialog = false
                                        }
                                    }
                                    Toast.makeText(context, "Record successfully inserted!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BubbleBlue, contentColor = Color.White),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text("Insert Entry", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Simulated Grid rendering component
@Composable
fun SimulatedRow(col: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Text(text = col, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BubbleBlue, modifier = Modifier.width(80.dp))
        Text(text = desc, fontSize = 11.sp, color = BubbleTextSecondary)
    }
}

// Subcomponent to draw clean bottom navigation tabs dynamically with selection pills
@Composable
fun BottomTabItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isActive) BubbleBlue else BubbleTextSecondary.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (isActive) FontWeight.Black else FontWeight.Bold,
            color = if (isActive) BubbleBlue else BubbleTextSecondary.copy(alpha = 0.7f)
        )
    }
}

// High polish launcher visual status indicator
@Composable
fun CircularStatusIndicator(
    isChecked: Boolean,
    onCheckedToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                if (isChecked) Brush.linearGradient(CleanBtnGradient) else Brush.linearGradient(
                    listOf(Color(0xFFF1F5F9), Color(0xFFE2E8F0))
                )
            )
            .border(
                1.dp,
                if (isChecked) Color.Transparent else BubbleBorder,
                CircleShape
            )
            .clickable { onCheckedToggle() },
        contentAlignment = Alignment.Center
    ) {
        if (isChecked) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

// Elegant cards representing individual listed movie databases
@Composable
fun MovieCardItem(
    movie: MovieRecord,
    isChecked: Boolean,
    onCheckToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isExpanded by remember { mutableStateOf(false) }
    var isCopiedE by remember { mutableStateOf(false) }
    var isCopiedF by remember { mutableStateOf(false) }

    LaunchedEffect(isCopiedE) {
        if (isCopiedE) {
            delay(1700)
            isCopiedE = false
        }
    }

    LaunchedEffect(isCopiedF) {
        if (isCopiedF) {
            delay(1700)
            isCopiedF = false
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 2.dp)
            .testTag("movie_item_card_${movie.id}")
            .shadow(
                elevation = if (isChecked) 4.dp else 1.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = BubbleBlue.copy(alpha = 0.2f),
                spotColor = BubbleBlue.copy(alpha = 0.2f)
            )
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = BubbleCardBg,
            contentColor = BubbleTextPrimary
        ),
        border = BorderStroke(1.dp, if (isChecked) BubbleBlue.copy(alpha = 0.5f) else BubbleBorder)
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                .padding(14.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left interactive Blue Radio Selector (imitating checkbox card layout)
                CircularStatusIndicator(
                    isChecked = isChecked,
                    onCheckedToggle = onCheckToggle
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = movie.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = BubbleTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Status Circle indicator (Green Completed vs Orange Progress)
                        val isTamilYear = movie.category.contains("2026")
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isTamilYear) BubbleGreen else BubbleOrange)
                        )
                        Text(
                            text = if (isTamilYear) "Completed" else "In process",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isTamilYear) BubbleGreen else BubbleOrange
                        )

                        Text(
                            text = "•",
                            fontSize = 10.sp,
                            color = BubbleTextMuted
                        )

                        Text(
                            text = movie.category.ifEmpty { "Uncategorized" },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = BubbleTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Expanding indicator chevron
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = BubbleTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Expanded Link Utilities Layout
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = BubbleBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BubbleBg.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (movie.sublink.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, contentDescription = null, tint = BubbleBlue, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Path: ${movie.sublink}",
                                fontSize = 11.sp,
                                color = BubbleTextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (movie.pageUrl.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    copyToClipboard(context, movie.pageUrl, "Col F Page")
                                    isCopiedF = true
                                    Toast.makeText(context, "Copied index URL!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopiedF) Icons.Rounded.CheckCircle else Icons.Rounded.FileCopy,
                                contentDescription = "Copy Col F URL",
                                tint = if (isCopiedF) BubbleGreen else BubbleBlue,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isCopiedF) "Copied Source Index URL!" else "Copy Page Link (Col F)",
                                fontSize = 11.sp,
                                color = if (isCopiedF) BubbleGreen else BubbleBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Movie Database Entry", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = BubbleBorder)
            Spacer(modifier = Modifier.height(10.dp))

            // Main Primary Core Launcher buttons (Always visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // COPY Core button (Column E)
                Button(
                    onClick = {
                        if (movie.link.isNotEmpty()) {
                            copyToClipboard(context, movie.link, "Movie")
                            isCopiedE = true
                            Toast.makeText(context, "Link (Col E) copied directly!", Toast.LENGTH_SHORT).show()
                        } else {
                            val fallback = movie.pageUrl.ifEmpty { movie.sublink }
                            if (fallback.isNotEmpty()) {
                                copyToClipboard(context, fallback, "Fallback Link")
                                isCopiedE = true
                                Toast.makeText(context, "Copied page fallback link!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No destination link URL available!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("copy_link_col_e"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCopiedE) BubbleGreen else BubbleBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = if (isCopiedE) Icons.Rounded.CheckCircle else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isCopiedE) "Copied Link!" else "Copy Link (Col E)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                // OPEN IN ULAA core launch button
                OutlinedButton(
                    onClick = {
                        val targetUrl = movie.link.ifEmpty { movie.pageUrl }
                        if (targetUrl.isNotEmpty()) {
                            launchUrlInUlaa(context, targetUrl)
                        } else {
                            Toast.makeText(context, "No URL link available to expand!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("open_ulaa"),
                    border = BorderStroke(1.dp, BubbleBlue),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BubbleBlue
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInBrowser,
                        contentDescription = "Ulaa Launcher Icon",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Open in Ulaa",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
}

private fun launchUrlInUlaa(context: Context, url: String) {
    val ulaaIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        setPackage("com.zoho.ulaa")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        context.startActivity(ulaaIntent)
    } catch (e: Exception) {
        val normalIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(normalIntent)
            Toast.makeText(context, "Ulaa Sandbox not present. Opened in default safety browser.", Toast.LENGTH_LONG).show()
        } catch (ex: Exception) {
            Toast.makeText(context, "No internet browser detected to unpack link!", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun AppLockScreen(
    correctPin: String,
    onUnlocked: () -> Unit
) {
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF3B82F6).copy(alpha = 0.2f), Color.Transparent),
                            radius = 180f
                        )
                    )
            ) {
                Canvas(modifier = Modifier.size(76.dp)) {
                    drawCircle(
                        brush = Brush.linearGradient(
                            listOf(Color(0xFF3B82F6), Color(0xFF60A5FA))
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0D1626))
                        .border(1.5.dp, Color(0xFF60A5FA).copy(alpha = 0.6f), CircleShape)
                ) {
                    Image(
                        painter = painterResource(R.drawable.premium_movie_link_logo_1790163614766),
                        contentDescription = "Movie Logo",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "MOVIE",
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                fontFamily = FontFamily.SansSerif,
                color = Color.White,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "SECURE PORTAL ENTRY",
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                letterSpacing = 2.sp
            )
            
            Spacer(modifier = Modifier.height(28.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val totalDots = correctPin.length.coerceAtLeast(4)
                for (i in 0 until totalDots) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (isFilled) Color(0xFF3B82F6) else Color(0xFF334155)
                            )
                            .border(
                                width = 1.5.dp,
                                color = if (isFilled) Color(0xFF60A5FA) else Color(0xFF475569),
                                shape = CircleShape
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(18.dp))
            
            Box(
                modifier = Modifier.height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFEF4444),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Enter your PIN code to decrypt dataset listings",
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val numPadRows = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "◀")
                )
                
                numPadRows.forEach { rowKeys ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        rowKeys.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                KeypadButton(
                                    key = key,
                                    onClick = {
                                        errorMessage = null
                                        if (key == "C") {
                                            enteredPin = ""
                                        } else if (key == "◀") {
                                            if (enteredPin.isNotEmpty()) {
                                                enteredPin = enteredPin.dropLast(1)
                                            }
                                        } else {
                                            if (enteredPin.length < correctPin.length) {
                                                enteredPin += key
                                                
                                                if (enteredPin.length == correctPin.length) {
                                                    if (enteredPin == correctPin) {
                                                        onUnlocked()
                                                    } else {
                                                        errorMessage = "ACCESS_DENIED: Invalid PIN"
                                                        enteredPin = ""
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadButton(
    key: String,
    onClick: () -> Unit
) {
    val isFunctional = key == "C" || key == "◀"
    val containerBg = if (isFunctional) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFF334155).copy(alpha = 0.3f)
    val textOrIconColor = if (isFunctional) Color(0xFF94A3B8) else Color.White
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(containerBg)
            .border(1.dp, Color(0xFF475569).copy(alpha = 0.4f), CircleShape)
            .clickable(onClick = onClick)
    ) {
        if (key == "◀") {
            Icon(
                imageVector = Icons.Rounded.Backspace,
                contentDescription = "Backspace",
                tint = textOrIconColor,
                modifier = Modifier.size(20.dp)
            )
        } else {
            Text(
                text = key,
                color = textOrIconColor,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
        }
    }
}

