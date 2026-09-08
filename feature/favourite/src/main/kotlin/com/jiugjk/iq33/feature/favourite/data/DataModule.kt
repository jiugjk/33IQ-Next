package com.jiugjk.iq33.feature.favourite.data

import androidx.room.Room
import com.jiugjk.iq33.feature.favourite.data.datasource.database.FavouriteDatabase
import com.jiugjk.iq33.feature.favourite.data.repository.BookmarkRepositoryImpl
import com.jiugjk.iq33.feature.favourite.domain.repository.BookmarkRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal val dataModule =
    module {
        singleOf(::BookmarkRepositoryImpl) { bind<BookmarkRepository>() }

        single {
            Room
                .databaseBuilder(get(), FavouriteDatabase::class.java, "Favourites.db")
                .build()
        }

        single { get<FavouriteDatabase>().savedQuestionDao() }
    }
