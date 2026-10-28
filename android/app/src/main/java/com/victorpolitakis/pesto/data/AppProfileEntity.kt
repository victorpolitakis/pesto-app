package com.victorpolitakis.pesto.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** The profile chosen for one app (C9, Apps screen). An app without a row uses the default profile. */
@Entity(tableName = "app_profiles")
data class AppProfileEntity(
    @PrimaryKey @ColumnInfo(name = "package") val packageName: String,
    @ColumnInfo(name = "profile_id") val profileId: String,
)
