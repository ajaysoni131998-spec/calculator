package com.example.ui.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.StatusStoryEntity
import com.example.ui.theme.*

@Composable
fun StatusTab(
    myDisplayName: String,
    stories: List<StatusStoryEntity>,
    onAddStoryClick: () -> Unit,
    onStoryClick: (StatusStoryEntity) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("status_tab")
    ) {
        // My Status Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onAddStoryClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(WaEmeraldDark),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = myDisplayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(WaEmerald)
                            .border(2.dp, WaChatBackground, CircleShape)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add status",
                            tint = Color.Black,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "My Status",
                        color = WaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Tap to add disappearing status update",
                        color = WaTextSecondary,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = onAddStoryClick,
                    modifier = Modifier.testTag("edit_status_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "New Text Status",
                        tint = WaEmerald
                    )
                }
            }

            Text(
                text = "RECENT UPDATES",
                color = WaTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (stories.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No recent status updates from peers.",
                        color = WaTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(stories, key = { it.id }) { story ->
                val bg = try {
                    Color(android.graphics.Color.parseColor(story.backgroundColorHex))
                } catch (e: Exception) {
                    WaEmerald
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onStoryClick(story) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .testTag("story_item_${story.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status ring circle
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .border(2.5.dp, WaStatusUnread, CircleShape)
                            .padding(3.dp)
                            .clip(CircleShape)
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = story.authorName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = story.authorName,
                            color = WaLightTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (story.storyType) {
                                    "IMAGE" -> Icons.Default.Image
                                    "VIDEO" -> Icons.Default.PlayArrow
                                    "LINK" -> Icons.Default.Share
                                    else -> Icons.Default.Edit
                                },
                                contentDescription = null,
                                tint = WaLightTealPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            val displayCaption = when (story.storyType) {
                                "IMAGE" -> story.caption.ifBlank { "Photo status" }
                                "VIDEO" -> story.caption.ifBlank { "Video status" }
                                "LINK" -> story.caption.ifBlank { story.linkUrl }
                                else -> story.caption
                            }
                            Text(
                                text = displayCaption,
                                color = WaLightTextSecondary,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
