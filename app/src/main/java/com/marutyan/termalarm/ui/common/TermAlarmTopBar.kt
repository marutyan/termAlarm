package com.marutyan.termalarm.ui.common
 
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.marutyan.termalarm.R

/**
 * 主要5画面の最上部に配置される共通の帯コンポーザブル。
 * アプリのアイコンと名称を表示し、右端の三点メニューから設定等の機能へ誘導するために用いる。
 */
@Composable
fun TermAlarmTopBar(
    onOpenSettings: () -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            AppIconMark()
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 0.01.em,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        TermAlarmOverflowMenu(
            onOpenSettings = onOpenSettings,
            onOpenPrivacyPolicy = onOpenPrivacyPolicy,
            onOpenAbout = onOpenAbout,
        )
    }
}

/**
 * 上の帯の左端に置くアプリのアイコン。
 * ランチャーのアイコンと同じ絵を出すため、単色の記号ではなくアダプティブアイコンの
 * 背景色と前景を重ねて描く。デザイン(design/LogoE.dc.html)の26dp・角丸7dpに合わせる。
 */
@Composable
private fun AppIconMark() {
    Box(
        modifier = Modifier
            .size(APP_ICON_SIZE)
            .clip(RoundedCornerShape(7.dp))
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            // アダプティブアイコンの前景は108dpの画に描かれ、実際に見えるのは中央の72dpだけ。
            // 26dpの枠へ見える部分をそのまま収めるため、108/72倍に広げて中央を切り取る
            modifier = Modifier.size(APP_ICON_SIZE * 108f / 72f),
        )
    }
}

/** 上の帯に出すアプリのアイコンの大きさ。デザインの26dpに合わせる。 */
private val APP_ICON_SIZE = 26.dp
