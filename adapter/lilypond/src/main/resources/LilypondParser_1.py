import os
import sys
from py4j.java_gateway import JavaGateway, GatewayParameters
import parce
from parce.lang.lilypond import LilyPond

# the ECCO process's gateway - on the port it passes in ECCO_PY4J_PORT (py4j's default otherwise)
gateway = JavaGateway(gateway_parameters=GatewayParameters(port=int(os.environ.get("ECCO_PY4J_PORT", "25333"))))
ep = gateway.entry_point

f = open(sys.argv[1], "r", -1, "UTF-8")
s = f.read()
f.close()

# Musical tokens (ECCO_LILYPOND_MUSICAL_TOKENS, set for a repository that records
# lilypond.musicalTokens=true in its .ecco/.settings - every new one, see LilypondPlugin): each
# note, rest and chord end is ONE token - pitch, octave marks, accidental marks, duration, dots,
# scaling - and carries what it means however it is spelled (absolute pitch, written-out
# duration), from lymodel's lybar: the lilypond-idea-plugin's python/ directory, from LYPYTHON
# when that names one, else installed (pip install <plugin repo>/python). Without lymodel the
# file is not read at all: plain tokens in a musical repository would differ from every token
# committed with musical ones, and silently so. A file whose notes lymodel cannot pair (\fixed,
# say) keeps plain tokens - the same ones in every commit.
meanings = {}
if os.environ.get("ECCO_LILYPOND_MUSICAL_TOKENS"):
    if os.environ.get("LYPYTHON"):
        sys.path.insert(0, os.environ["LYPYTHON"])
    try:
        from lymodel.lybar.normalize import note_values
    except ImportError as e:
        print("This repository reads LilyPond with musical tokens (lilypond.musicalTokens=true in its\n"
              ".ecco/.settings), which need lymodel: %s.\n"
              "Install it into the python that runs parce (python -m pip install <lilypond-idea-plugin>/python)\n"
              "or set LYPYTHON to that python/ directory." % e, file=sys.stderr)
        sys.exit(3)
    try:
        meanings = note_values(s)
    except Exception as e:
        print("no musical tokens: %s" % e, file=sys.stderr)

# Variable contexts (ECCO_LILYPOND_VARIABLE_CONTEXTS, set for a repository that records
# lilypond.variableContexts=true in its .ecco/.settings - every new one, see LilypondPlugin): each
# top-level assignment becomes a context named after its variable, from lymodel's parse tree, around
# parce's own contexts. parce names the contexts it lexes in - every voice's notes are in a
# `LilyPond.musiclist` - so two voices' contexts are alike and only their order tells them apart: a
# version that moved a voice had its notes matched with another voice's, and a feature committed
# before the move put its accents on the wrong notes. `LilyPond.assignment.SopranoVoice` is the
# soprano in every commit. The context changes only between top-level statements, where parce's own
# stack is empty, so it always nests. Without lymodel such a repository is not read at all, as with
# musical tokens.
assignments = []
if os.environ.get("ECCO_LILYPOND_VARIABLE_CONTEXTS"):
    if os.environ.get("LYPYTHON") and os.environ["LYPYTHON"] not in sys.path:
        sys.path.insert(0, os.environ["LYPYTHON"])
    try:
        from lymodel.lybar import backend
        from lymodel.lybar.backend import items
    except ImportError as e:
        print("This repository reads LilyPond with variable contexts (lilypond.variableContexts=true in its\n"
              ".ecco/.settings), which need lymodel: %s.\n"
              "Install it into the python that runs parce (python -m pip install <lilypond-idea-plugin>/python)\n"
              "or set LYPYTHON to that python/ directory." % e, file=sys.stderr)
        sys.exit(3)
    try:
        assignments = [(node.position, node.end_position(), "LilyPond.assignment." + node.name())
                       for node in backend.parsed(s) if isinstance(node, items.Assignment)]
    except Exception as e:
        print("no variable contexts: %s" % e, file=sys.stderr)
outer = None      # the assignment context open, its name
depth = 0         # parce's own context depth, suppressed pitch contexts included


def enclose(pos):
    """Opens the assignment context [pos] is in, closing the one before - at depth 0 only."""
    global outer
    name = next((n for start, end, n in assignments if start <= pos < end), None)
    if name != outer:
        flush()
        if outer is not None:
            ep.popContext(-1)
        if name is not None:
            ep.pushContext(name)
        outer = name


ITEM = {"Text.Music.Pitch", "Text.Music.Rest", "Delimiter.Chord.End"}
ATTACHED = {"Text.Music.Pitch.Octave", "Text.Music.Pitch.Accidental", "Literal.Number.Duration",
            "Literal.Number.Duration.Dot", "Literal.Number.Duration.Scaling"}
pending = None    # [pos, text, action] of a note being joined


def flush():
    global pending
    if pending is not None:
        meaning = meanings.get(pending[0])
        if meaning is None:
            ep.addToken(pending[0], pending[1], pending[2])
        else:
            ep.addToken(pending[0], pending[1], pending[2], meaning)
        pending = None


def token(pos, text, action):
    global pending
    if pending is not None and action in ATTACHED and pos == pending[0] + len(pending[1]):
        pending[1] += text
        return
    flush()
    if meanings and action in ITEM:
        pending = [pos, text, action]
    else:
        ep.addToken(pos, text, action)


lastPos = 0
suppressed = 0    # LilyPond.pitch contexts not pushed - see below
for e in parce.events(LilyPond.root, s):
    pop = 0
    lexemes = list(e.lexemes)
    # A chord's duration comes with the event that closes the chord context: join it to the
    # pending `>` first, or the chord end and its duration were two tokens.
    while pending is not None and lexemes and str(lexemes[0][2]) in ATTACHED \
            and lexemes[0][0] == pending[0] + len(pending[1]):
        pending[1] += lexemes[0][1]
        lastPos = lexemes[0][0] + len(lexemes[0][1])
        lexemes.pop(0)
    if e.target:
        pop = e.target.pop
        depth += pop
        if suppressed and pop < 0:
            closed = min(suppressed, -pop)
            suppressed -= closed
            pop += closed
        push = [c.fullname for c in e.target.push]
        # Octave marks come in a LilyPond.pitch context of their own: `fis,2` has one, `fis2`
        # has none. With musical tokens they are part of the note, and the context would be a
        # difference between two spellings of it.
        if meanings and push and all(name == "LilyPond.pitch" for name in push):
            suppressed += len(push)
            push = []
        if pop or push:
            flush()
        ep.popContext(pop)
        if assignments and depth == 0 and lexemes:
            enclose(lexemes[0][0])
        depth += len(e.target.push)
        if lexemes:
            first = lexemes[0]
            if first[0] > lastPos:
                flush()
                ep.addWhitespace(lastPos, s[lastPos:first[0]])
                lastPos = first[0] + len(first[1])
        for name in push:
            ep.pushContext(name)

    for tpl in lexemes:
        if assignments and depth == 0:
            enclose(tpl[0])
        if tpl[0] > lastPos:
            flush()
            ep.addWhitespace(lastPos, s[lastPos:tpl[0]])
        token(tpl[0], tpl[1], str(tpl[2]))
        lastPos = tpl[0] + len(tpl[1])

flush()
ep.addWhitespace(lastPos, s[lastPos:])
