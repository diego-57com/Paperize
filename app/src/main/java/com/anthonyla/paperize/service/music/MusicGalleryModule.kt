package com.anthonyla.paperize.service.music

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MusicGalleryModule {

    @Provides
    @Singleton
    fun provideMusicGalleryDatabase(@ApplicationContext context: Context): MusicGalleryDatabase {
        return Room.databaseBuilder(
            context,
            MusicGalleryDatabase::class.java,
            "music_wallpaper_gallery.db"
        )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideMusicArtworkDao(database: MusicGalleryDatabase): MusicArtworkDao =
        database.musicArtworkDao()
}
