package com.alyaqdhan.mybattery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescanPullToRefreshBox(
    isRefreshing: Boolean,
    onRescan: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh    = onRescan,
        state        = state,
        modifier     = modifier,
        indicator    = {
            PullToRefreshDefaults.Indicator(
                state        = state,
                isRefreshing = false,
                modifier     = Modifier.align(Alignment.TopCenter),
                color        = Color.White
            )
        },
        content = content
    )
}

data class SetupStep(
    val icon: Painter,
    val title: String,
    val desc: String,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
    val onLongAction: (() -> Unit)? = null
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SetupStepRow(number: Int, step: SetupStep) {
    val card   = Color(0xFF121212)
    val border = Color(0xFF262626)
    val tp     = Color.White
    val ts     = Color(0xFF8E8E93)

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(38.dp)
                .background(card, CircleShape)
                .border(1.dp, border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "$number",
                color = tp,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 8.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    step.icon, null,
                    tint     = Color(0xFFCCCCCC),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    step.title,
                    color = tp,
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    step.desc,
                    color    = ts,
                    style    = MaterialTheme.typography.bodySmall.copy(lineHeight = 17.sp),
                    modifier = Modifier.weight(1f)
                )
                if (step.actionLabel != null && step.onAction != null) {
                    Surface(
                        shape  = RoundedCornerShape(6.dp),
                        color  = Color(0xFF1A1A1A),
                        border = BorderStroke(1.dp, Color(0xFF333333))
                    ) {
                        Text(
                            step.actionLabel,
                            modifier = Modifier
                                .combinedClickable(
                                    onClick     = step.onAction,
                                    onLongClick = step.onLongAction
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            color    = Color.White,
                            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}
