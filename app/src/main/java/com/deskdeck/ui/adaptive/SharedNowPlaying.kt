package com.deskdeck.ui.adaptive

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.deskdeck.ui.theme.LocalMotion

/** The scopes of the layout switch, so the album art and title can travel between layouts. */
class SharedLayoutScopes(val transition: SharedTransitionScope, val visibility: AnimatedVisibilityScope)

val LocalSharedLayoutScopes = compositionLocalOf<SharedLayoutScopes?> { null }

/** Keys shared by the full card and the mini player. */
object SharedKeys {
    const val ART = "nowPlaying.art"
    const val TITLE = "nowPlaying.title"
}

/**
 * Lets this element (the album art) move and resize between the full and the mini layout. Does
 * nothing outside a layout switch, in previews, or with Remove animations.
 */
@Composable
fun Modifier.sharedNowPlayingElement(key: String): Modifier {
    val scopes = LocalSharedLayoutScopes.current ?: return this
    if (LocalMotion.current.reduced) return this
    return with(scopes.transition) {
        this@sharedNowPlayingElement.sharedElement(rememberSharedContentState(key), scopes.visibility)
    }
}

/** Like [sharedNowPlayingElement], for content that changes look on the way (text sizes): crossfades. */
@Composable
fun Modifier.sharedNowPlayingBounds(key: String): Modifier {
    val scopes = LocalSharedLayoutScopes.current ?: return this
    if (LocalMotion.current.reduced) return this
    return with(scopes.transition) {
        this@sharedNowPlayingBounds.sharedBounds(rememberSharedContentState(key), scopes.visibility)
    }
}
