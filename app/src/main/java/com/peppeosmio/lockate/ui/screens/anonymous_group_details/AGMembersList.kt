package com.peppeosmio.lockate.ui.screens.anonymous_group_details

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.peppeosmio.lockate.R
import com.peppeosmio.lockate.domain.anonymous_group.AGMember
import com.peppeosmio.lockate.utils.DateTimeUtils
import kotlinx.datetime.format

@Composable
fun AGMembersList(
    authenticatedMemberId: String,
    members: List<AGMember>,
    modifier: Modifier = Modifier,
    onLocateClick: (memberId: String) -> Unit
) {
    Box {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(members, key = { _, member -> member.id }) { _, member ->
                MemberRow(
                    modifier = Modifier.animateItem(),
                    member = member,
                    isMe = authenticatedMemberId == member.id,
                    onLocateClick = {
                        onLocateClick(member.id)
                    })
            }
        }
    }
}

@Composable
fun MemberRow(
    modifier: Modifier = Modifier, member: AGMember, isMe: Boolean, onLocateClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(
                    top = if (isMe) 12.dp else 6.dp, bottom = 12.dp, start = 12.dp, end = 6.dp
                )
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    modifier = Modifier.weight(1f), text = if (isMe) {
                        "(You) ${member.name}"
                    } else {
                        member.name
                    }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
                )
                if (!isMe && member.lastLocationRecord != null) {
                    IconButton(onClick = onLocateClick) {
                        Icon(
                            painter = painterResource(R.drawable.outline_location_searching_24),
                            contentDescription = "Find member on map",
                        )
                    }
                }
            }
            Text(
                text = "Id: ${member.id}", style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Joined: ${
                    member.createdAt.format(
                        DateTimeUtils.DATE_FORMAT
                    )
                }", style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Last seen: ${member.lastLocationRecord?.timestamp?.format(DateTimeUtils.DATE_FORMAT) ?: "never"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
