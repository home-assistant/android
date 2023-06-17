package io.homeassistant.companion.android.settings.url.views

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.PlusOne
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.homeassistant.companion.android.settings.url.HeadersViewModel
import kotlinx.coroutines.handleCoroutineException
import io.homeassistant.companion.android.common.R as commonR

//private fun createHeaderRow(headerName: String, headerValue: String):  {
//
//}

@Composable
fun HeadersView(
    headersViewModel: HeadersViewModel
) {
    Column(
        modifier = Modifier.padding(vertical = 5.dp)
    ) {
        val scaffoldState = rememberScaffoldState()
        val headerEntries = headersViewModel.headers.entries.toList()
        Scaffold(
            scaffoldState = scaffoldState
        ) { padding ->
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(headerEntries.size) {index ->
                    val headerEntry = headerEntries[index]
                    Row( modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp) ) {
                        Text(
                            text = stringResource(commonR.string.pref_headers_heading, index + 1),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Row( modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp) ) {
                        TextField(
                            label = {Text(stringResource(commonR.string.pref_headers_name_label))},
                            value = headerEntry.key,
                            onValueChange = {
                                headersViewModel.removeHeader(headerEntry.key)
                                headersViewModel.setHeader(it, headerEntry.value)
                            },
                        )
                    }
                    Row( modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp) ) {
                        TextField(
                            label = {Text(stringResource(commonR.string.pref_headers_value_label))},
                            value = headerEntry.value,
                            onValueChange = { headersViewModel.setHeader(headerEntry.key, it) },
                        )
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(commonR.string.pref_headers_remove_header),
                            tint = colorResource(commonR.color.colorWarning),
                            modifier = Modifier
                                .clickable { headersViewModel.removeHeader(headerEntry.key) }
                                .size(48.dp)
                                .padding(all = 12.dp)
                        )

                        if (index == (headerEntries.size - 1)) {
                            Icon(
                                imageVector = Icons.Default.PlusOne,
                                contentDescription = stringResource(commonR.string.pref_headers_add_header),
                                tint = colorResource(commonR.color.colorPrimary),
                                modifier = Modifier
                                    .clickable { headersViewModel.setHeader("", "") }
                                    .size(48.dp)
                                    .padding(all = 12.dp)
                            )
                        }

                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                }
            }
        }
    }
}