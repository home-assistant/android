package io.homeassistant.companion.android.noconnection

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.BaseActivity
import io.homeassistant.companion.android.R
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.util.compose.HomeAssistantAppTheme

class NoConnectionActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            HomeAssistantAppTheme {
                NoConnectionScreen()
            }
        }
    }
}

@Composable
fun NoConnectionScreen() {
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
    ) {
        Header()
        Content()
    }
}

@Composable
fun Header() {
    Surface(
        color = MaterialTheme.colors.primary,
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight()
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.app_icon_launch),
                contentDescription = "",
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )

            // TODO check Badge component
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = "",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = 18.dp, x = -(13.dp))
                    .shadow(elevation = 8.dp, shape = CircleShape) // Added shadow her
                    .background(Color.White)
                    .padding(4.dp),
                tint = Color.Red
            )
        }
    }
}

@Composable
fun Content() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .padding(16.dp),
    ) {
        Column {
            // Title
            Text(
                text = stringResource(commonR.string.error_connection_failed),
                style = MaterialTheme.typography.h4,
                fontWeight = FontWeight.Bold,
            )
            // Description
            Text(
                text = stringResource(commonR.string.webview_error),
                style = MaterialTheme.typography.body1,
                modifier = Modifier.padding(top = 16.dp)
            )
            // Connection info
            Surface(
                shape = MaterialTheme.shapes.small,
                color = colorBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("The app is currently connecting to")
                    Text("http://127.0.0.1:8123", style = MaterialTheme.typography.caption, fontWeight = FontWeight.Bold, color = Color.Blue)
                }
            }

            MoreDetails()
            SupportUs()
            Action()
        }
        GetHelp()
    }
}

@Composable
private fun ColumnScope.GetHelp() {
    Text(text = "Get some help from the community", modifier = Modifier.fillMaxWidth().padding(top = 24.dp), textAlign = TextAlign.Center)
    Row(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 16.dp)
            .fillMaxWidth()
            .align(Alignment.CenterHorizontally),
        horizontalArrangement = Arrangement.Center
    ) {
        val buttonModifier = Modifier
            .padding(horizontal = 10.dp) // Add padding between buttons
            .size(48.dp) // Set the size for all buttons

        OutlinedButton(onClick = { /*TODO*/ }, modifier = buttonModifier) {
            Icon(imageVector = Icons.Outlined.Newspaper, contentDescription = "Documentation", modifier = Modifier.size(24.dp))
        }
        OutlinedButton(onClick = { /*TODO*/ }, modifier = buttonModifier) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.discord), contentDescription = "Home Assistant Discord", modifier = Modifier.size(24.dp))
        }
        OutlinedButton(onClick = { /*TODO*/ }, modifier = buttonModifier) {
            Icon(imageVector = ImageVector.vectorResource(R.drawable.github), contentDescription = "Github Android Companion app", modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun MoreDetails(defaultExpanded: Boolean = false) {
    ExpandableSection(title = "More details", modifier = Modifier.padding(top = 16.dp), defaultExpanded = defaultExpanded) {
        Column(modifier = Modifier.padding(start = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Description", style = MaterialTheme.typography.h6)
            Text("Could not connect to the server")

            Text("Error", style = MaterialTheme.typography.h6)
            Text("IllegalStateException")
        }
    }
}

@Composable
private fun ColumnScope.Action() {
    Row(
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        OutlinedButton(
            onClick = { /*TODO*/ },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = "")
            // TODO auto retry
            Text("Retry")
        }
        OutlinedButton(
            onClick = { /*TODO*/ },
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(imageVector = Icons.Filled.Settings, contentDescription = "")
            Text("Open Settings")
        }
    }
}

@Composable
private fun SupportUs() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(MaterialTheme.shapes.small)
            .background(colorBackground)
    ) {
        // val text = "It seems that you cannot reach home for some reason. Did you consider using supporting Nabu Casa? It would also give you access to your home from everywhere."
        val text = "If you'd like secure access end to end encrypted to your home from anywhere while supporting the Home Assistant project, consider using"
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            textAlign = TextAlign.Start,
            style = MaterialTheme.typography.body2
        )
        Image(
            imageVector = ImageVector.vectorResource(R.drawable.nabucasa),
            contentDescription = "Nabu casa",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .height(40.dp)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
fun ExpandableSectionTitle(modifier: Modifier = Modifier, isExpanded: Boolean, title: String) {
    val icon = if (isExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown

    Row(modifier = modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.body1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
        )
        Icon(
            modifier = Modifier.size(32.dp),
            imageVector = icon,
            tint = MaterialTheme.colors.primary,
            contentDescription = "Open/Close detail"
        )
    }
}

private val colorBackground = Color(0xFFF0F0F0)

@Composable
fun ExpandableSection(
    title: String,
    modifier: Modifier = Modifier,
    defaultExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(defaultExpanded) }
    Column(
        modifier = modifier
            .clickable { isExpanded = !isExpanded }
            .clip(MaterialTheme.shapes.small)
            .background(color = colorBackground)
            .fillMaxWidth()
    ) {
        ExpandableSectionTitle(isExpanded = isExpanded, title = title)

        AnimatedVisibility(
            modifier = Modifier
                .background(colorBackground)
                .fillMaxWidth(),
            visible = isExpanded
        ) {
            content()
        }
    }
}

@Preview
@Composable
fun NoConnectionScreenPreview() {
    HomeAssistantAppTheme {
        NoConnectionScreen()
    }
}

@Preview
@Composable
private fun MoreDetailsPreview() {
    MoreDetails(true)
}
