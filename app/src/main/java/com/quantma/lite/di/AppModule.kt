package com.quantma.lite.di

import android.content.Context
import androidx.room.Room
import com.quantma.lite.data.local.db.AgentConfigDao
import com.quantma.lite.data.local.db.AppDatabase
import com.quantma.lite.data.local.db.ChatMessageDao
import com.quantma.lite.data.local.db.ChatSessionDao
import com.quantma.lite.data.local.db.HookDao
import com.quantma.lite.data.local.db.RuleDao
import com.quantma.lite.data.local.db.SkillDao
import com.quantma.lite.data.repository.ChatRepositoryImpl
import com.quantma.lite.data.agent.GitOperationsAdapter
import com.quantma.lite.data.local.preferences.GitCredentialsStore
import com.quantma.lite.data.repository.FileRepositoryImpl
import com.quantma.lite.data.repository.GitRepositoryImpl
import com.quantma.lite.domain.repository.ChatRepository
import com.quantma.lite.domain.repository.FileRepository
import com.quantma.lite.domain.repository.GitRepository
import com.quantma.lite.sas.CommandParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "quantma.db"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideChatMessageDao(database: AppDatabase): ChatMessageDao {
        return database.chatMessageDao()
    }

    @Provides
    fun provideChatSessionDao(database: AppDatabase): ChatSessionDao {
        return database.chatSessionDao()
    }

    @Provides
    fun provideAgentConfigDao(database: AppDatabase): AgentConfigDao {
        return database.agentConfigDao()
    }

    @Provides
    fun provideSkillDao(database: AppDatabase): SkillDao {
        return database.skillDao()
    }

    @Provides
    fun provideRuleDao(database: AppDatabase): RuleDao {
        return database.ruleDao()
    }

    @Provides
    fun provideHookDao(database: AppDatabase): HookDao {
        return database.hookDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        chatMessageDao: ChatMessageDao,
        chatSessionDao: ChatSessionDao
    ): ChatRepository {
        return ChatRepositoryImpl(chatMessageDao, chatSessionDao)
    }

    @Provides
    @Singleton
    fun provideFileRepository(): FileRepository {
        return FileRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideGitRepository(): GitRepository {
        return GitRepositoryImpl()
    }

    @Provides
    @Singleton
    fun provideGitOperationsAdapter(
        gitRepository: GitRepository,
        credentialsStore: GitCredentialsStore
    ): GitOperationsAdapter =
        GitOperationsAdapter(gitRepository, credentialsStore)

    @Provides
    @Singleton
    fun provideCommandParser(): CommandParser = CommandParser()
}
