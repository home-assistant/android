package io.homeassistant.companion.android.search.views

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.rememberScalingLazyListState
import io.homeassistant.companion.android.common.R
import io.homeassistant.companion.android.home.views.TimeText
import io.homeassistant.companion.android.search.SearchViewModel
import io.homeassistant.companion.android.theme.WearAppTheme
import io.homeassistant.companion.android.views.ThemeLazyColumn

@Composable
fun SearchResultView(
    searchViewModel: SearchViewModel
) {

    val scrollState = rememberScalingLazyListState()

    WearAppTheme {
        Scaffold(
            positionIndicator = {
                if (scrollState.isScrollInProgress)
                    PositionIndicator(scalingLazyListState = scrollState)
            },
            timeText = { TimeText(visible = !scrollState.isScrollInProgress) }
        ) {
            ThemeLazyColumn(
                state = scrollState
            ) {
                item {
                    Text(
                        text = searchViewModel.searchResult.value.ifEmpty { stringResource(R.string.no_search_results) },
                        modifier = Modifier.padding(40.dp)
                    )
                }
                if (searchViewModel.speechResult.value.isNotEmpty())
                    item {
                        Text(
                            text = searchViewModel.speechResult.value,
                            modifier = Modifier.padding(top = 10.dp, start = 30.dp)
                        )
                    }
            }
        }
    }
}
