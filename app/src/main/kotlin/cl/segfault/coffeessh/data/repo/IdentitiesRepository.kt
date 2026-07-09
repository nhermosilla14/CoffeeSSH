package cl.segfault.coffeessh.data.repo

import cl.segfault.coffeessh.data.crypto.KeystoreCrypto
import cl.segfault.coffeessh.data.db.IdentityDao
import cl.segfault.coffeessh.data.db.IdentityEntity
import kotlinx.coroutines.flow.Flow

/** Decrypted identity fields, only ever held in memory for the editor form. */
data class IdentityDraft(
    val id: Long? = null,
    val nickname: String = "",
    val username: String = "",
    val password: String = "",
    val privateKey: String = "",
)

class IdentitiesRepository(
    private val dao: IdentityDao,
    private val crypto: KeystoreCrypto,
) {

    fun observeAll(): Flow<List<IdentityEntity>> = dao.observeAll()

    suspend fun getDraft(id: Long): IdentityDraft? {
        val entity = dao.getById(id) ?: return null
        return IdentityDraft(
            id = entity.id,
            nickname = entity.nickname,
            username = entity.username,
            password = entity.passwordEnc?.let(crypto::decryptToString).orEmpty(),
            privateKey = entity.privateKeyEnc?.let(crypto::decryptToString).orEmpty(),
        )
    }

    suspend fun save(draft: IdentityDraft): Long {
        val passwordEnc = draft.password.takeIf { it.isNotEmpty() }?.let(crypto::encryptString)
        val privateKeyEnc = draft.privateKey.takeIf { it.isNotEmpty() }?.let(crypto::encryptString)
        return if (draft.id == null) {
            dao.insert(
                IdentityEntity(
                    nickname = draft.nickname.trim(),
                    username = draft.username.trim(),
                    passwordEnc = passwordEnc,
                    privateKeyEnc = privateKeyEnc,
                ),
            )
        } else {
            val existing = checkNotNull(dao.getById(draft.id)) { "Identity ${draft.id} not found" }
            dao.update(
                existing.copy(
                    nickname = draft.nickname.trim(),
                    username = draft.username.trim(),
                    passwordEnc = passwordEnc,
                    privateKeyEnc = privateKeyEnc,
                ),
            )
            draft.id
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
