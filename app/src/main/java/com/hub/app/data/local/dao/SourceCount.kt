package com.hub.app.data.local.dao

/** Projektionsergebnis für [MessageDao.observeSourceCounts]. */
data class SourceCount(
    val sourceKey: String,
    val count: Int
)
