package com.zstream.android.ui.screens

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * @deprecated Search and Discovery logic has been consolidated into [HomeViewModel].
 * This class is kept temporarily for backward compatibility if needed, but is no longer used
 * by the primary [SearchScreen].
 */
@HiltViewModel
class SearchViewModel @Inject constructor() : ViewModel()
