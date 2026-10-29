package com.spaco.app.core.data.repository

import com.spaco.app.core.data.IdGenerator
import com.spaco.app.core.data.db.SkillDao
import com.spaco.app.core.data.db.SkillEntity
import com.spaco.app.core.domain.model.Skill
import com.spaco.app.core.domain.repository.SkillRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SkillRepositoryImpl @Inject constructor(
    private val dao: SkillDao,
    private val clock: Clock,
    private val ids: IdGenerator,
) : SkillRepository {

    override fun observeAll(): Flow<List<Skill>> =
        dao.observeAll().map { rows -> rows.map(SkillEntity::toDomain) }

    override suspend fun getById(id: String): Skill? = dao.getById(id)?.toDomain()

    override suspend fun getByName(name: String): Skill? = dao.getByName(name.trim())?.toDomain()

    override suspend fun create(name: String): Skill {
        val skill = Skill(id = ids.newId(), name = name, createdAt = clock.instant())
        dao.upsert(skill.toEntity())
        return skill
    }

    override suspend fun rename(id: String, name: String) = dao.rename(id, name)

    override suspend fun archive(id: String) = dao.setArchivedAt(id, clock.millis())

    override suspend fun restore(id: String) = dao.setArchivedAt(id, null)
}
