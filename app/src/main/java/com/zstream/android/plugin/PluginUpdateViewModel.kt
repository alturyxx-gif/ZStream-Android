package com.zstream.android.plugin

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PluginUpdateViewModel @Inject constructor(
    val pluginManager: PluginManager,
) : ViewModel()
