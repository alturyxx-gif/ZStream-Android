package com.zstream.android.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.zstream.android.R

internal val groupIconOptions: List<Pair<String, Int>> = listOf(
    "CAT" to R.drawable.ic_group_cat,
    "WEED" to R.drawable.ic_group_weed,
    "USER_GROUP" to R.drawable.ic_group_user_group,
    "COUCH" to R.drawable.ic_group_couch,
    "MOBILE" to R.drawable.ic_group_mobile,
    "TICKET" to R.drawable.ic_group_ticket,
    "SATURN" to R.drawable.ic_group_saturn,
    "HEADPHONES" to R.drawable.ic_group_headphones,
    "TV" to R.drawable.ic_group_tv,
    "GHOST" to R.drawable.ic_group_ghost,
    "COFFEE" to R.drawable.ic_group_coffee,
    "FIRE" to R.drawable.ic_group_fire,
    "MEGAPHONE" to R.drawable.ic_group_megaphone,
    "DRAGON" to R.drawable.ic_group_dragon,
    "RISING_STAR" to R.drawable.ic_group_rising_star,
    "CLOUD_ARROW_UP" to R.drawable.ic_group_cloud_arrow_up,
    "WAND" to R.drawable.ic_group_wand,
    "CLAPPER_BOARD" to R.drawable.ic_group_clapper_board,
    "BOOKMARK" to R.drawable.ic_player_bookmark_filled,
    "FIREFOX" to R.drawable.ic_group_firefox,
    "CHROME" to R.drawable.ic_group_chrome,
    "SAFARI" to R.drawable.ic_group_safari,
    "ORION" to R.drawable.ic_group_orion,
    "EDGE" to R.drawable.ic_group_edge,
)

@Composable
internal fun groupIconPainter(key: String): Painter = when (key.uppercase()) {
    "CAT", "WEED", "USER_GROUP", "COUCH", "MOBILE", "TICKET", "SATURN", "HEADPHONES",
    "TV", "GHOST", "COFFEE", "FIRE", "MEGAPHONE", "DRAGON", "RISING_STAR",
    "CLOUD_ARROW_UP", "WAND", "CLAPPER_BOARD", "BOOKMARK", "FIREFOX", "CHROME",
    "SAFARI", "ORION", "EDGE" -> painterResource(
        groupIconOptions.first { it.first == key.uppercase() }.second
    )
    "FOLDER" -> painterResource(R.drawable.ic_player_bookmark_filled)
    "MOVIE", "THEATERS" -> painterResource(R.drawable.ic_group_clapper_board)
    "FAVORITE" -> painterResource(R.drawable.ic_player_bookmark_filled)
    "GROUPS" -> painterResource(R.drawable.ic_group_user_group)
    "SMART_TOY" -> painterResource(R.drawable.ic_group_dragon)
    "DEVICE_HUB" -> painterResource(R.drawable.ic_group_cloud_arrow_up)
    else -> painterResource(R.drawable.ic_player_bookmark_filled)
}
