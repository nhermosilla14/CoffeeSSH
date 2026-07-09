# Golden fixtures for the terminal engine

`GoldenReplayTest` (in `terminal/src/test/kotlin/.../GoldenReplayTest.kt`) replays real
byte captures from `vim`, `tmux`, and a plain shell through our `Terminal` and checks
the resulting grid against an independent oracle rendering computed by
[`pyte`](https://github.com/selectel/pyte) (a separate, independently-implemented
Python VT100/xterm emulator). Matching a second, unrelated implementation is a much
stronger signal than hand-written "expected" strings, which could just as easily
encode the same misunderstanding as the code under test.

The fixtures themselves live in `terminal/src/test/resources/golden/`:

- `<name>.bin` — the exact raw bytes captured from the real program's pty
- `<name>.meta.txt` — terminal size + oracle cursor position
- `<name>.expected.txt` — pyte's rendering of the final screen, one line per row

## Regenerating

Requires `uv` (or any way to get a Python env) and network access to install `pyte`:

```sh
uv venv /tmp/pyte-venv --python 3.12
uv pip install --python /tmp/pyte-venv/bin/python pyte
/tmp/pyte-venv/bin/python terminal/golden-fixtures/capture_fixtures.py /tmp/fixtures
cp /tmp/fixtures/*.bin /tmp/fixtures/*.expected.txt /tmp/fixtures/*.meta.txt terminal/src/test/resources/golden/
```

Then `./gradlew :terminal:test --tests "*.GoldenReplayTest"`.

## Known oracle limitation

Plain `pyte.Screen` has no concept of the alternate screen buffer: it silently
ignores DECSET `1049`/`47`/`1047`, so it can't oracle the "restore the main screen on
exit" transition (confirmed by reading `pyte/screens.py` — `set_mode`/`reset_mode`
only branch on DECCOLM/DECOM/DECSCNM/DECTCEM). The `vim_basic` fixture therefore
captures vim *mid-session* rather than after `:q!`; the exit transition is instead
covered by a dedicated hand-written test
(`TerminalScrollRegionTest.alternateScreenHasNoScrollbackAndIsDiscardedOnExit`).

Also worth knowing: pyte 0.8.2 forwards a `private=True` kwarg to any CSI handler
following a private-marker (`?`) sequence, but several `Screen` methods don't declare
`**kwargs` to swallow it, so real captures from vim/tmux (which both probe terminal
capabilities) crash the vanilla library. `capture_fixtures.py` monkey-patches around
this at fixture-generation time; it's a limitation of pyte itself, not of our engine.
