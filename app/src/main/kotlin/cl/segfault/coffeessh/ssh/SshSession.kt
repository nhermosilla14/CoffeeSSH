package cl.segfault.coffeessh.ssh

import android.util.Log
import cl.segfault.coffeessh.data.db.KnownHostDao
import cl.segfault.coffeessh.terminal.Terminal
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.userauth.UserAuthException
import net.schmizz.sshj.userauth.method.AuthKeyboardInteractive
import net.schmizz.sshj.userauth.method.AuthMethod
import net.schmizz.sshj.userauth.method.AuthPassword
import net.schmizz.sshj.userauth.method.AuthPublickey
import net.schmizz.sshj.userauth.method.PasswordResponseProvider
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource

/**
 * One SSH connection: owns the sshj client/session/shell plumbing and a [Terminal] fed
 * from the shell's output. Runs its own [CoroutineScope] (background dispatcher) so
 * every method here is safe to call directly from the UI thread - callers never block.
 */
class SshSession(
    val connectionId: Long,
    private val knownHostDao: KnownHostDao,
) {
    val terminal = Terminal(rows = 24, cols = 80)

    private val _state = MutableStateFlow<SshSessionState>(SshSessionState.Idle)
    val state: StateFlow<SshSessionState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    @Volatile private var client: SSHClient? = null
    @Volatile private var shell: Session.Shell? = null
    private var hostKeyDecision: CompletableDeferred<Boolean>? = null

    /** Called once, right after authentication succeeds and the shell is up. */
    var onConnected: (() -> Unit)? = null

    init {
        terminal.onResponse = { bytes -> sendInput(bytes) }
    }

    fun start(host: String, port: Int, username: String, password: String?, privateKeyPem: String?) {
        val current = _state.value
        if (current is SshSessionState.Connecting || current is SshSessionState.Authenticating ||
            current is SshSessionState.Connected
        ) {
            return
        }
        scope.launch { connectBlocking(host, port, username, password, privateKeyPem) }
    }

    private suspend fun connectBlocking(
        host: String,
        port: Int,
        username: String,
        password: String?,
        privateKeyPem: String?,
    ) {
        _state.value = SshSessionState.Connecting
        var localClient: SSHClient? = null
        try {
            val verifier = TofuHostKeyVerifier(knownHostDao)
            val c = SSHClient()
            c.addHostKeyVerifier(verifier)
            c.connectTimeout = 10_000
            c.timeout = 15_000
            c.connect(host, port)
            localClient = c
            client = c

            // Safe to block on a human decision here: kex has already completed, so we're
            // no longer racing sshj's internal kex-completion timeout (see the class doc
            // on TofuHostKeyVerifier for why the verifier itself can't do this directly).
            when (val result = verifier.lastResult) {
                is TofuHostKeyVerifier.Result.NeedsDecision -> {
                    val accepted = requestHostKeyDecision(
                        SshSessionState.AwaitingHostKeyConfirmation(
                            host = result.host,
                            port = result.port,
                            keyType = result.keyType,
                            fingerprint = result.fingerprint,
                            isChanged = result.isChanged,
                        ),
                    )
                    if (!accepted) {
                        _state.value = SshSessionState.Failed("Host key rejected")
                        return
                    }
                    verifier.persist(result)
                }
                else -> {} // already trusted (or, unexpectedly, null - connect() succeeded either way)
            }

            c.connection.keepAlive.keepAliveInterval = 15

            _state.value = SshSessionState.Authenticating
            authenticate(c, username, password, privateKeyPem)

            val session = c.startSession()
            session.allocatePTY(Terminal.TERM_TYPE, terminal.cols, terminal.rows, 0, 0, emptyMap())
            val sh = session.startShell()
            shell = sh

            _state.value = SshSessionState.Connected
            onConnected?.invoke()

            readLoop(sh)
            _state.value = SshSessionState.Disconnected
        } catch (e: Exception) {
            Log.e(TAG, "Session $connectionId failed to connect", e)
            _state.value = SshSessionState.Failed(describeError(e))
        } finally {
            runCatching { shell?.close() }
            runCatching { localClient?.disconnect() }
            shell = null
            client = null
        }
    }

    private suspend fun requestHostKeyDecision(pending: SshSessionState.AwaitingHostKeyConfirmation): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        hostKeyDecision = deferred
        _state.value = pending
        return deferred.await()
    }

    /** Resolves a pending [SshSessionState.AwaitingHostKeyConfirmation] shown by the UI. */
    fun confirmHostKey(accept: Boolean) {
        hostKeyDecision?.complete(accept)
        hostKeyDecision = null
    }

    private fun authenticate(client: SSHClient, username: String, password: String?, privateKeyPem: String?) {
        val methods = mutableListOf<AuthMethod>()
        if (!privateKeyPem.isNullOrBlank()) {
            val keyProvider = client.loadKeys(privateKeyPem, null, null)
            methods += AuthPublickey(keyProvider)
        }
        if (!password.isNullOrBlank()) {
            val finder = object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>): CharArray = password.toCharArray()
                override fun shouldRetry(resource: Resource<*>): Boolean = false
            }
            methods += AuthPassword(finder)
            methods += AuthKeyboardInteractive(PasswordResponseProvider(finder))
        }
        check(methods.isNotEmpty()) { "Identity has neither a private key nor a password" }
        client.auth(username, methods)
    }

    private fun readLoop(sh: Session.Shell) {
        val input = sh.inputStream
        val buffer = ByteArray(8192)
        while (true) {
            val n = try {
                input.read(buffer)
            } catch (e: IOException) {
                -1
            }
            if (n < 0) break
            if (n > 0) terminal.write(buffer.copyOf(n))
        }
    }

    /** Bytes typed/produced locally (keyboard, extra-keys bar, paste, CPR/DA replies). */
    fun sendInput(bytes: ByteArray) {
        scope.launch {
            writeMutex.withLock {
                try {
                    shell?.outputStream?.let { out ->
                        out.write(bytes)
                        out.flush()
                    }
                } catch (e: IOException) {
                    // Session is closing/closed; readLoop will notice and flip the state.
                }
            }
        }
    }

    fun resize(rows: Int, cols: Int) {
        terminal.resize(rows, cols)
        scope.launch {
            runCatching { shell?.changeWindowDimensions(cols, rows, 0, 0) }
        }
    }

    fun disconnect() {
        scope.launch {
            runCatching { shell?.close() }
            runCatching { client?.disconnect() }
        }
    }

    /** Tears down the session's background scope; call when the session is discarded for good. */
    fun destroy() {
        disconnect()
        scope.cancel()
    }

    private fun describeError(e: Exception): String = when (e) {
        is UserAuthException -> "Authentication failed"
        else -> e.message ?: e.javaClass.simpleName
    }

    private companion object {
        const val TAG = "SshSession"
    }
}
