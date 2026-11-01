package dev.personal.tvlauncher.home

import dev.personal.tvlauncher.channels.*

fun retainSnapshot(previous: List<ChannelRow>, state: ChannelLoadState): List<ChannelRow> =
    if (state is ChannelLoadState.Ready) state.rows else previous
