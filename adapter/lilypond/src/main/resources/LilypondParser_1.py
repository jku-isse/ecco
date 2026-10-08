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

# Musical tokens (ECCO_LILYPOND_MUSICAL_TOKENS, set by -Decco.lilypond.musicalTokens=true): each
# note, rest and chord end is ONE token - pitch, octave marks, accidental marks, duration, dots,
# scaling - and carries what it means however it is spelled (absolute pitch, written-out
# duration), from lybar in the lilypond-idea-plugin's python/ (LYPYTHON). Without lybar, or
# when it cannot pair this file's notes, tokens stay as they were and go by their text.
meanings = {}
if os.environ.get("ECCO_LILYPOND_MUSICAL_TOKENS"):
    if os.environ.get("LYPYTHON"):
        sys.path.insert(0, os.environ["LYPYTHON"])
    try:
        from lybar.normalize import note_values
        meanings = note_values(s)
    except Exception as e:
        print("no musical tokens: %s" % e, file=sys.stderr)

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
    if e.target:
        pop = e.target.pop
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
        first = e.lexemes[0]
        if first[0] > lastPos:
            flush()
            ep.addWhitespace(lastPos, s[lastPos:first[0]])
            lastPos = first[0] + len(first[1])
        for name in push:
            ep.pushContext(name)

    for tpl in e.lexemes:
        if tpl[0] > lastPos:
            flush()
            ep.addWhitespace(lastPos, s[lastPos:tpl[0]])
        token(tpl[0], tpl[1], str(tpl[2]))
        lastPos = tpl[0] + len(tpl[1])

flush()
ep.addWhitespace(lastPos, s[lastPos:])
