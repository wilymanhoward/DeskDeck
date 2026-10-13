package com.deskdeck.ui.notifications

import androidx.lifecycle.ViewModel
import com.deskdeck.media.Banner
import com.deskdeck.media.NotificationBanners
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class BannerViewModel @Inject constructor(private val banners: NotificationBanners) : ViewModel() {
    val queue: StateFlow<List<Banner>> = banners.queue
    val centerOpen: StateFlow<Boolean> = banners.centerOpen
    val active: StateFlow<List<Banner>> = banners.active

    fun dismiss(key: String) = banners.dismiss(key)
    fun clear(key: String) = banners.clear(key)
    fun clearAll() = banners.clearAll()
    fun openCenter() = banners.openCenter()
    fun closeCenter() = banners.closeCenter()
}
