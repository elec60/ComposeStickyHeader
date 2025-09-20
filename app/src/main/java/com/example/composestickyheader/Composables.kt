package com.example.composestickyheader

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private object ImageConstants {
    val COLLAPSED_HEIGHT = 125.dp
    val DEFAULT_HEIGHT = 300.dp
    val OVERSCROLL_HEIGHT = 450.dp
}

private data class ScrollData(
    val isOverScrolling: Boolean = false,
    val isScrollingUp: Boolean = false,
    val changeY: Float = 0f
)

@Composable
fun CollapsingHeaderList(modifier: Modifier = Modifier) {
    val state = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var imageHeight by remember { mutableStateOf(ImageConstants.DEFAULT_HEIGHT) }
    val animatable = remember { Animatable(0f) }


    /**
     * Determines if the list is at the very top position.
     * This is crucial for enabling overscroll behavior - we only allow
     * the header to expand beyond DEFAULT_HEIGHT when at the top.
     */
    val isTop by remember {
        derivedStateOf {
            state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0
        }
    }

    var scrollingData by remember { mutableStateOf(ScrollData()) }

    /**
     * PROGRESS CALCULATION MATHEMATICS:
     * =================================
     *
     * Progress represents how "collapsed" the header is, ranging from 0.0 to 1.0:
     * - 0.0 = Fully expanded (DEFAULT_HEIGHT)
     * - 1.0 = Fully collapsed (COLLAPSED_HEIGHT)
     *
     * Formula: progress = (currentHeight - defaultHeight) / (collapsedHeight - defaultHeight)
     *
     * Example with our constants:
     * - DEFAULT_HEIGHT = 300dp, COLLAPSED_HEIGHT = 125dp
     * - Range = 125 - 300 = -175dp
     *
     * When imageHeight = 300dp: progress = (300-300)/(-175) = 0/(-175) = 0.0 (expanded)
     * When imageHeight = 212.5dp: progress = (212.5-300)/(-175) = -87.5/(-175) = 0.5 (halfway)
     * When imageHeight = 125dp: progress = (125-300)/(-175) = -175/(-175) = 1.0 (collapsed)
     *
     */
    val progress by remember {
        derivedStateOf {
            val defaultHeight = ImageConstants.DEFAULT_HEIGHT
            val collapseHeight = ImageConstants.COLLAPSED_HEIGHT

            ((imageHeight - defaultHeight) / (collapseHeight - defaultHeight))
                .coerceIn(0f, 1f)
        }
    }

    /**
     * Reactive effect that updates image height based on scroll interactions.
     *
     * OVERSCROLL LOGIC:
     * - Only triggered when isTop = true AND changeY > 0 (pulling down)
     * - Expands header from DEFAULT_HEIGHT toward OVERSCROLL_HEIGHT
     *
     * COLLAPSE LOGIC:
     * - Triggered when changeY < 0 (scrolling up)
     * - Shrinks header from current height toward COLLAPSED_HEIGHT
     */
    LaunchedEffect(scrollingData) {
        val deltaHeight = with(density) { scrollingData.changeY.toDp() }

        if (scrollingData.isOverScrolling) {
            imageHeight = (imageHeight + deltaHeight).coerceAtMost(
                ImageConstants.OVERSCROLL_HEIGHT
            )
        }

        if (scrollingData.isScrollingUp) {
            imageHeight = (imageHeight + deltaHeight).coerceAtLeast(
                ImageConstants.COLLAPSED_HEIGHT,
            )
        }
    }

    /**
     * NestedScrollConnection handles scroll events from the LazyColumn.
     * This prevents the list from scrolling when we're in certain header states.
     */
    val nestedScrollConnection = remember {
        createNestedScrollConnection(
            imageHeight = { imageHeight }
        )
    }

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .nestedScroll(nestedScrollConnection)
                .pointerInput(Unit) {
                    /**
                     * POINTER INPUT HANDLING:
                     * =======================
                     *
                     * This block captures raw touch events to drive the header animations.
                     * We use PointerEventPass.Initial to get events before they reach child components.
                     */
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            val drag = event.changes.firstOrNull()

                            drag?.let { pointer ->
                                /**
                                 * ACTIVE DRAG HANDLING:
                                 * ====================
                                 *
                                 * When user is actively dragging:
                                 * 1. Calculate position change (changeY)
                                 * 2. Determine interaction type:
                                 *    - isOverScrolling: At top + pulling down (changeY > 0)
                                 *    - isScrollingUp: Moving up regardless of position (changeY < 0)
                                 */
                                if (pointer.pressed && pointer.positionChanged()) {
                                    val change = pointer.positionChange()
                                    val isOverScrolling = isTop && change.y > 0
                                    val isScrollingUp = change.y < 0

                                    scrollingData = ScrollData(
                                        isOverScrolling = isOverScrolling,
                                        isScrollingUp = isScrollingUp,
                                        changeY = change.y
                                    )
                                }

                                /**
                                 * RELEASE HANDLING & SNAP ANIMATION:
                                 * ==================================
                                 *
                                 * When user releases their finger, we animate to the nearest stable state.
                                 *
                                 * SNAP LOGIC:
                                 * -----------
                                 * 1. If height is between COLLAPSED and DEFAULT:
                                 *    - Calculate midpoint = (300 + 125) / 2 = 212.5dp
                                 *    - If current < midpoint: snap to COLLAPSED (125dp)
                                 *    - If current >= midpoint: snap to DEFAULT (300dp)
                                 *
                                 * 2. If height >= DEFAULT: always snap to DEFAULT
                                 *
                                 * ANIMATION TECHNIQUE:
                                 * -------------------
                                 * - Use Animatable from 0.0 to 1.0 over 300ms
                                 * - Interpolate between currentHeight and targetHeight using the animation value
                                 * - Formula: newHeight = currentHeight + (targetHeight - currentHeight) * animationValue
                                 *
                                 * This creates smooth eased transitions instead of instant snapping.
                                 */
                                if (event.type == PointerEventType.Release) {
                                    val targetHeight =
                                        if (imageHeight >= ImageConstants.COLLAPSED_HEIGHT && imageHeight < ImageConstants.DEFAULT_HEIGHT) {
                                            val average =
                                                (ImageConstants.DEFAULT_HEIGHT + ImageConstants.COLLAPSED_HEIGHT) / 2f
                                            if (imageHeight < average) {
                                                ImageConstants.COLLAPSED_HEIGHT
                                            } else {
                                                ImageConstants.DEFAULT_HEIGHT
                                            }
                                        } else {
                                            ImageConstants.DEFAULT_HEIGHT
                                        }

                                    coroutineScope.launch {
                                        val currentImageHeight = imageHeight
                                        animatable.snapTo(0f)
                                        animatable.animateTo(
                                            targetValue = 1f,
                                            animationSpec = tween(300)
                                        ) {
                                            imageHeight =
                                                currentImageHeight + (targetHeight - currentImageHeight) * value
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
        ) {
            HeaderImage(
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(imageHeight),
                progress = progress
            )

            ContentList(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                state = state
            )
        }
        HeaderToolbar(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun HeaderToolbar(
    modifier: Modifier = Modifier,
    onBackClick: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onBackClick,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }

        IconButton(
            onClick = onMenuClick,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Menu",
                tint = Color.White
            )
        }
    }
}

/**
 * Header image component with animated title overlay.
 *
 * TITLE ANIMATION EFFECTS:
 * ========================
 *
 * 1. SCALE ANIMATION:
 *    - Formula: scale = (1 - progress).coerceAtLeast(0.8f)
 *    - When progress = 0.0 (expanded): scale = 1.0 (full size)
 *    - When progress = 1.0 (collapsed): scale = 0.8 (80% size)
 *    - Creates shrinking effect as header collapses
 *
 * 2. HORIZONTAL OFFSET:
 *    - Formula: offsetX = 50.dp * progress
 *    - When progress = 0.0: offsetX = 0dp (original position)
 *    - When progress = 1.0: offsetX = 50dp (moved right)
 *    - Creates sliding effect toward the right
 *
 * 3. BACKGROUND OPACITY:
 *    - Formula: alpha = 0.6f * (1 - progress)
 *    - When progress = 0.0: alpha = 0.6 (60% opacity background)
 *    - When progress = 1.0: alpha = 0.0 (transparent background)
 *    - Background fades out as header collapses
 *
 * @param progress Collapse progress from 0.0 (expanded) to 1.0 (collapsed)
 */
@Composable
private fun HeaderImage(modifier: Modifier = Modifier, progress: Float) {
    val offset = 50.dp
    Box(modifier = modifier) {
        Image(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(id = R.drawable.image),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )

        Text(
            text = "Header Title",
            modifier = Modifier
                .scale((1 - progress).coerceAtLeast(0.8f))
                .offset(x = offset * progress, y = 0.dp)
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f * (1 - progress)),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}


@Composable
private fun ContentList(
    modifier: Modifier = Modifier,
    state: androidx.compose.foundation.lazy.LazyListState
) {
    LazyColumn(
        modifier = modifier,
        state = state,
    ) {
        items(100) { index ->
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(10.dp),
                text = "Item $index"
            )
        }
    }
}


/**
 * Creates a NestedScrollConnection that controls scroll consumption for collapsing header behavior.
 *
 * SCROLL CONSUMPTION LOGIC:
 * • Consumes ALL scroll when header is overscrolled (> 300dp) or transitioning (125dp-300dp)
 * • Allows normal list scrolling ONLY when header is fully collapsed (= 125dp)
 *
 */
private fun createNestedScrollConnection(
    imageHeight: () -> Dp
): NestedScrollConnection = object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val currentHeight = imageHeight()

        // Check if header is expanded beyond normal size (overscrolled)
        val overScrolled = currentHeight > ImageConstants.DEFAULT_HEIGHT

        // Check if header is in transition between collapsed and default size
        val scrollingToTop = (currentHeight <= ImageConstants.DEFAULT_HEIGHT) &&
                (currentHeight > ImageConstants.COLLAPSED_HEIGHT)

        return when {
            overScrolled -> {
                // Consume all scroll - header must return to normal size first
                Offset(x = 0f, y = available.y)
            }
            scrollingToTop -> {
                // Consume all scroll - header is transitioning, prevent list scroll
                Offset(x = 0f, y = available.y)
            }
            else -> {
                // Header is stable (fully collapsed or at default) - allow list scrolling
                Offset.Zero
            }
        }
    }
}

