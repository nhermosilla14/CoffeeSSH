#!/usr/bin/env python3
"""Captures raw PTY output from a real program and computes an independent oracle
rendering of the final screen via `pyte` (a separately-implemented VT100/xterm engine).

Used to generate golden fixtures for CoffeeSSH's terminal engine tests: we replay the
exact same captured bytes through our Kotlin `Terminal` and assert its grid matches
what pyte says a correct terminal would show. This catches real interpretation bugs
that hand-written "expected" strings could accidentally rubber-stamp.

Usage: see build_fixtures() at the bottom for the concrete fixtures generated.
"""
import fcntl
import os
import pty
import select
import signal
import struct
import termios
import time


def _set_winsize(fd: int, rows: int, cols: int) -> None:
    fcntl.ioctl(fd, termios.TIOCSWINSZ, struct.pack("HHHH", rows, cols, 0, 0))


def capture(cmd, cols, rows, steps, idle_timeout=0.4, hard_timeout=20, env_extra=None):
    """Runs `cmd` in a pty of the given size, feeding `steps` (delay_seconds, bytes)
    in order, and returns every byte the child wrote until it exits or goes idle.
    """
    pid, fd = pty.fork()
    if pid == 0:
        os.environ["TERM"] = "xterm-256color"
        os.environ["COLUMNS"] = str(cols)
        os.environ["LINES"] = str(rows)
        os.environ["LANG"] = "en_US.UTF-8"
        os.environ["LC_ALL"] = "en_US.UTF-8"
        if env_extra:
            os.environ.update(env_extra)
        os.execvp(cmd[0], cmd)
        os._exit(1)

    _set_winsize(fd, rows, cols)
    output = bytearray()
    start = time.time()
    step_idx = 0
    last_data = time.time()

    while True:
        if step_idx < len(steps) and time.time() - start >= steps[step_idx][0]:
            os.write(fd, steps[step_idx][1])
            step_idx += 1
        try:
            r, _, _ = select.select([fd], [], [], 0.05)
        except OSError:
            break
        if fd in r:
            try:
                chunk = os.read(fd, 65536)
            except OSError:
                break
            if not chunk:
                break
            output += chunk
            last_data = time.time()
        elif step_idx >= len(steps) and (time.time() - last_data) > idle_timeout:
            break
        if time.time() - start > hard_timeout:
            break

    try:
        os.kill(pid, signal.SIGKILL)
    except ProcessLookupError:
        pass
    try:
        os.waitpid(pid, 0)
    except ChildProcessError:
        pass
    return bytes(output)


def oracle_screen(raw: bytes, cols: int, rows: int):
    import pyte

    # pyte 0.8.2 forwards a `private=True` kwarg to *any* CSI handler when the
    # private marker (`?`) was present, but several Screen methods (SGR, device
    # status reports, ...) don't declare **kwargs to swallow it, so real-world
    # captures from vim/tmux (which both probe terminal capabilities) crash it.
    # We only use pyte as an offline oracle to build fixtures, so patch every
    # Screen method to silently ignore that kwarg rather than patch pyte itself.
    if not getattr(pyte, "_coffeessh_patched", False):
        for name, member in list(vars(pyte.screens.Screen).items()):
            if callable(member) and not name.startswith("__"):
                def _tolerant(fn):
                    def wrapper(*args, **kwargs):
                        kwargs.pop("private", None)
                        return fn(*args, **kwargs)
                    return wrapper
                setattr(pyte.screens.Screen, name, _tolerant(member))
        pyte._coffeessh_patched = True

    screen = pyte.Screen(cols, rows)
    stream = pyte.Stream(screen)
    # pyte.Stream expects str; decode permissively since some captures may contain
    # transient invalid sequences from a resized/killed process.
    stream.feed(raw.decode("utf-8", errors="replace"))
    return list(screen.display), screen.cursor.x, screen.cursor.y


def write_fixture(name: str, raw: bytes, cols: int, rows: int, out_dir: str):
    lines, cx, cy = oracle_screen(raw, cols, rows)
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(out_dir, f"{name}.bin"), "wb") as f:
        f.write(raw)
    with open(os.path.join(out_dir, f"{name}.meta.txt"), "w", encoding="utf-8") as f:
        f.write(f"cols={cols}\n")
        f.write(f"rows={rows}\n")
        f.write(f"cursor_x={cx}\n")
        f.write(f"cursor_y={cy}\n")
    with open(os.path.join(out_dir, f"{name}.expected.txt"), "w", encoding="utf-8") as f:
        for line in lines:
            f.write(line + "\n")
    print(f"{name}: captured {len(raw)} bytes, {len(lines)}x{cols} oracle screen, cursor=({cx},{cy})")


def key(s: str) -> bytes:
    return s.encode("utf-8")


ESC = b"\x1b"


def build_fixtures(out_dir: str):
    # 1) Plain shell output: wrapping + scrolling, no curses, easiest sanity fixture.
    raw = capture(
        ["/bin/bash", "--norc", "--noprofile"],
        cols=20,
        rows=6,
        steps=[
            (0.1, key("PS1='$ '\n")),
            (0.2, key("for i in $(seq 1 30); do echo \"line $i 0123456789\"; done\n")),
            (0.6, key("exit\n")),
        ],
    )
    write_fixture("plain_wrap_scroll", raw, 20, 6, out_dir)

    # 2) vim: alt screen, cursor addressing, status line, basic text.
    # Deliberately does NOT quit vim: pyte's base Screen class has no concept of the
    # alternate screen buffer at all (it silently ignores DECSET 1049/47/1047), so it
    # can't oracle the "restore main screen on exit" transition - that's covered by a
    # dedicated hand-written unit test instead. This fixture captures vim mid-session,
    # which pyte CAN correctly oracle (it just doesn't know it's a separate buffer).
    raw = capture(
        ["/usr/bin/vim", "-u", "NONE", "-N", "-c", "set nowrap noswapfile"],
        cols=40,
        rows=10,
        steps=[
            (0.3, key("iHello CoffeeSSH\nSecond line\x1b")),
            (0.4, key(":set number\n")),
            (0.6, key("")),
        ],
    )
    write_fixture("vim_basic", raw, 40, 10, out_dir)

    # 3) tmux: nested alt screen + status bar (scroll-region-like fixed line) + colors.
    # Force a plain bash shell inside the pane (not the host's zsh/theme) so the
    # fixture is deterministic and doesn't depend on this machine's dotfiles.
    session = "coffeessh_golden"
    raw = capture(
        [
            "/usr/bin/tmux", "-f", "/dev/null", "new-session", "-s", session,
            "/bin/bash", "--norc", "--noprofile",
        ],
        cols=50,
        rows=12,
        steps=[
            (0.3, key("PS1='tmux$ '\n")),
            (0.5, key("echo hello from tmux\n")),
            (0.7, key("printf '\\033[31mred\\033[0m \\033[32mgreen\\033[0m\\n'\n")),
            (0.9, key("")),
        ],
        hard_timeout=6,
    )
    # tmux keeps running (it's a server-attached session) - kill it explicitly afterwards.
    os.system(f"tmux -f /dev/null kill-session -t {session} 2>/dev/null")
    write_fixture("tmux_basic", raw, 50, 12, out_dir)


if __name__ == "__main__":
    import sys

    out = sys.argv[1] if len(sys.argv) > 1 else "fixtures"
    build_fixtures(out)
